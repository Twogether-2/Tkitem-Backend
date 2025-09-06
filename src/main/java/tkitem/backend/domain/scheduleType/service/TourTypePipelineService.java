package tkitem.backend.domain.scheduleType.service;

import co.elastic.clients.elasticsearch.core.BulkRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.scheduleType.classification.RuleClassifier;
import tkitem.backend.domain.scheduleType.dto.TourDetailScheduleRowDto;
import tkitem.backend.domain.scheduleType.mapper.TourDetailScheduleMapper;
import tkitem.backend.domain.scheduleType.mapper.TourScheduleTypeMapper;
import tkitem.backend.domain.scheduleType.service.ScheduleEsService.LearningData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class TourTypePipelineService {
    private final TourDetailScheduleMapper tdsMapper;
    private final ScheduleEsService esService;
    private final EmbeddingService embeddingService;
    private final RuleClassifier rule;
    private final TourScheduleTypeMapper tstMapper;
    private final GenerativeLabelService genSvc;

    private static final double MIN_SCORE = 0.65;
    private static final double MIN_MARGIN = 0.10;
    private static final double CONFIDENCE_THRESHOLD = 0.80; // 학습 데이터 축적을 위한 신뢰도 임계값 (KNN, LLM용)
    private static final int PENDING_FLUSH = 2000;
    private static final String ES_INDEX = "tour_detail_schedule_v1";

    private static final Set<String> MOVE = Set.of("FLIGHT", "TRANSFER");
    private static final Set<String> VIEW = Set.of("SIGHTSEEING","LANDMARK", "MUSEUM_HERITAGE","PARK_NATURE","SHOW");


    // 한번만 돌리는 메인 엔트리
    @Transactional
    public void runOnce(int batchSize) throws Exception {
        esService.ensureIndexExistsOrThrow();

        int offset = 0;
        List<TourDetailScheduleRowDto> knnTargets = new ArrayList<>();
        List<TourDetailScheduleRowDto> llmPending = new ArrayList<>();
        long lastProcessedId = -1;

        // 분류 통계 카운터
        int ruleTried = 0, ruleSuccess = 0;
        int knnNeeded = 0, knnSuccess = 0;
        int llmNeeded = 0, llmSuccess = 0;

        while (true) {
            // 1) DB 배치 조회
            List<TourDetailScheduleRowDto> rows = tdsMapper.selectBatchForIndexing(offset, batchSize);
            if (rows == null || rows.isEmpty()) break;

            // 2) 임베딩 일괄 생성
            List<String> textsToEmbed = rows.stream()
                    .map(r -> (r.getTitle() == null ? "" : r.getTitle()) + " " + (r.getDescription() == null ? "" : r.getDescription()))
                    .map(s -> s.replaceAll("\\s+", " ").trim())
                    .toList();
            List<float[]> embeddings = embeddingService.embedAll(textsToEmbed);


            List<LearningData> learningDataToSave = new ArrayList<>();
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            for (int i = 0; i < rows.size(); i++) {
                var r = rows.get(i);
                String combined = textsToEmbed.get(i);
                float[] vec = embeddings.get(i);

                // ES 문서 전송 (대상 인덱스/별칭은 설정값)
                Map<String, Object> d = toEsDoc(r, r.getTitle(), r.getDescription(), combined, vec);
                bulk.operations(op -> op.index(idx -> idx
                        .index(ES_INDEX)
                        .id(String.valueOf(r.getTourDetailScheduleId()))
                        .document(d)
                ));

                // defaultType 기반 분류 제어: PLACE는 분류 생략
                String dt = r.getDefaultType();
                if (dt != null && dt.equalsIgnoreCase("PLACE")) {
                    continue;
                }

                // 3) 룰 1차 분류 → 확신 낮으면 pending
                var scores = rule.score(r.getTitle(), r.getDescription(), r.getDefaultType());
                var top = rule.top2(scores);

                if (top.top1Type == null) {
                    log.warn("[RULE-FAIL] No rule match for tdsId={}. Adding to KNN queue. Title='{}', Desc='{}', DefaultType='{}'",
                            r.getTourDetailScheduleId(), r.getTitle(), r.getDescription(), r.getDefaultType());
                    knnTargets.add(r);
                    knnNeeded++;
                } else {
                    ruleTried++;

                    if (dt != null && dt.equalsIgnoreCase("MEAL") && top.top1Score == 0.0) {
                        Long typeId = tstMapper.findScheduleTypeIdByName("MEAL");
                        if (typeId != null) {
                            tstMapper.upsertTourScheduleType(r.getTourDetailScheduleId(), typeId, 0.0);
                            ruleSuccess++;
                        }
                        continue;
                    }

                    if (dt != null && (dt.equalsIgnoreCase("MEAL") || dt.equalsIgnoreCase("ACCOMMODATION"))) {
                        Long typeId = tstMapper.findScheduleTypeIdByName(top.top1Type);
                        if (typeId != null) {
                            tstMapper.upsertTourScheduleType(r.getTourDetailScheduleId(), typeId, top.top1Score);
                            ruleSuccess++;
                            var result = List.of(new GenerativeLabelService.Result(top.top1Type, top.top1Score));
                            accumulateLearningData(learningDataToSave, result, combined, vec, false); // 룰 기반은 항상 저장
                        }
                        continue;
                    }

                    double top1 = top.top1Score;
                    double top2 = top.top2Type == null ? 0.0 : top.top2Score;
                    double margin = top1 - top2;

                    if (top1 >= MIN_SCORE) {
                        if(margin >= MIN_MARGIN) {
                            // 4) 확신 충분 → DB UPSERT + ES 업데이트 예약
                            Long typeId = tstMapper.findScheduleTypeIdByName(top.top1Type);
                            if (typeId != null) {
                                tstMapper.upsertTourScheduleType(r.getTourDetailScheduleId(), typeId, top1);
                                ruleSuccess++;
                                var result = List.of(new GenerativeLabelService.Result(top.top1Type, top1));
                                accumulateLearningData(learningDataToSave, result, combined, vec, false); // 룰 기반은 항상 저장
                            }
                        } else {
                            // 근소차이 : 상충 계열이면 KNN/LLM
                            if(isConflict(top.top1Type, top.top2Type)) {
                                log.warn("[RULE-FAIL] Conflict detected for tdsId={}. Top1='{}', Top2='{}'. Adding to KNN queue. Title='{}', Desc='{}', DefaultType='{}'",
                                        r.getTourDetailScheduleId(), top.top1Type, top.top2Type, r.getTitle(), r.getDescription(), r.getDefaultType());
                                knnTargets.add(r);
                                knnNeeded++;
                            } else { // 동일 계열이면 채택
                                Long typeId = tstMapper.findScheduleTypeIdByName(top.top1Type);
                                if (typeId != null) {
                                    tstMapper.upsertTourScheduleType(r.getTourDetailScheduleId(), typeId, top1);
                                    ruleSuccess++;
                                    var result = List.of(new GenerativeLabelService.Result(top.top1Type, top1));
                                    accumulateLearningData(learningDataToSave, result, combined, vec, false); // 룰 기반은 항상 저장
                                }
                            }
                        }
                    } else {
                        // top1 자체가 낮으면 생성형으로 보완
                        log.warn("[RULE-FAIL] Low score for tdsId={}. Top1='{}'({}), Score < {}. Adding to KNN queue. Title='{}', Desc='{}', DefaultType='{}'",
                                r.getTourDetailScheduleId(), top.top1Type, String.format("%.2f", top.top1Score), MIN_SCORE, r.getTitle(), r.getDescription(), r.getDefaultType());
                        knnTargets.add(r);
                        knnNeeded++;
                    }
                }
                lastProcessedId = r.getTourDetailScheduleId();

            }

            esService.bulk(bulk.build());
            esService.saveLabel(learningDataToSave); // 룰 기반 학습 데이터 일괄 저장
            learningDataToSave.clear();

            // 4) KNN Top-3 저장 -> 미달 항목은 LLM 보완 대상으로 이동
            if(!knnTargets.isEmpty()){
                Map<Long, List<GenerativeLabelService.Result>> knnMap = genSvc.classifyBatchByKNN(knnTargets);

                // KNN 성공 건에 대한 임베딩도 일괄 생성
                List<TourDetailScheduleRowDto> knnSuccessRows = new ArrayList<>();
                Map<Long, List<GenerativeLabelService.Result>> knnSuccessResults = new HashMap<>();

                for (var r : knnTargets) {
                    Long tdsId = r.getTourDetailScheduleId();
                    var list = knnMap.getOrDefault(tdsId, List.of());

                    if (list.isEmpty() || list.get(0).score() < MIN_SCORE) {
                        llmPending.add(r);
                        llmNeeded++;
                    } else {
                        knnSuccess++;
                        knnSuccessRows.add(r);
                        knnSuccessResults.put(tdsId, list);
                        for (var res : list) {
                            Long typeId = tstMapper.findScheduleTypeIdByName(res.typeName());
                            if (typeId != null) {
                                tstMapper.upsertTourScheduleType(tdsId, typeId, res.score());
                            }
                        }
                    }
                }

                if (!knnSuccessRows.isEmpty()) {
                    List<String> knnTextsToEmbed = knnSuccessRows.stream()
                            .map(r -> (r.getTitle() == null ? "" : r.getTitle()) + " " + (r.getDescription() == null ? "" : r.getDescription()))
                            .map(s -> s.replaceAll("\\s+", " ").trim())
                            .toList();
                    List<float[]> knnEmbeddings = embeddingService.embedAll(knnTextsToEmbed);

                    for (int i = 0; i < knnSuccessRows.size(); i++) {
                        var r = knnSuccessRows.get(i);
                        var list = knnSuccessResults.get(r.getTourDetailScheduleId());
                        accumulateLearningData(learningDataToSave, list, knnTextsToEmbed.get(i), knnEmbeddings.get(i), true);
                    }
                }

                esService.saveLabel(learningDataToSave); // KNN 기반 학습 데이터 일괄 저장
                knnTargets.clear();
            }

            // 5) pending 임계치 도달 시 즉시 생성형 분류 -> DB/ES 반영
            if (llmPending.size() >= PENDING_FLUSH) {
                int added = flushPendingWithGen(llmPending);
                llmSuccess += added;
                llmPending.clear();
            }

            offset += rows.size();

            log.info("[BATCH] rows={}, lastId={}", rows.size(), lastProcessedId);
            log.info("[RULE] tried={} success={} rate={}%", ruleTried, ruleSuccess, ruleTried == 0 ? 0.0 : Math.round((ruleSuccess * 10000.0 / ruleTried)) / 100.0);
            log.info("[KNN ] needed={} success={} rate={}%", knnNeeded, knnSuccess, knnNeeded == 0 ? 0.0 : Math.round((knnSuccess * 10000.0 / knnNeeded)) / 100.0);
            log.info("[LLM ] needed={} success={} (flushed on threshold)", llmNeeded, llmSuccess);
        }

        if (!llmPending.isEmpty()) {
            int added = flushPendingWithGen(llmPending);
            llmSuccess += added;
            llmPending.clear();
        }

        log.info("[SUMMARY] RULE: tried={} success={} rate={}%", ruleTried, ruleSuccess, ruleTried == 0 ? 0.0 : Math.round((ruleSuccess * 10000.0 / ruleTried)) / 100.0);
        log.info("[SUMMARY] KNN : needed={} success={} rate={}%", knnNeeded, knnSuccess, knnNeeded == 0 ? 0.0 : Math.round((knnSuccess * 10000.0 / knnNeeded)) / 100.0);
        log.info("[SUMMARY] LLM : needed={} success={}", llmNeeded, llmSuccess);
        log.info("pipeline completed");
    }

    private Map<String, Object> toEsDoc(TourDetailScheduleRowDto r, String title, String desc, String combined, float[] vec) {
        Map<String, Object> doc = new ConcurrentHashMap<>();
        doc.put("tour_detail_schedule_id", r.getTourDetailScheduleId());
        doc.put("tour_id", r.getTourId());
        doc.put("schedule_date", r.getScheduleDate());
        doc.put("sort_order", r.getSortOrder());
        doc.put("country_name", r.getCountryName());
        doc.put("city_name", r.getCityName());
        doc.put("title", title == null ? "" : title);
        doc.put("description", desc == null ? "" : desc);
        doc.put("combined_text", combined);
        doc.put("embedding", vec);
        return doc;
    }

    // KNN 분류 -> 부족 시 LLM 분류 -> 라벨 임베딩 → DB 반영
    private int flushPendingWithGen(List<TourDetailScheduleRowDto> pending) throws Exception {
        if (pending == null || pending.isEmpty()) return 0;

        // LLM 보조 분류 (배치 호출)
        Map<Long, List<GenerativeLabelService.Result>> results = genSvc.classifyBatchByLLM(pending);
        int successCount = 0;
        List<LearningData> learningDataToSave = new ArrayList<>();

        // LLM 분류 건에 대한 임베딩 일괄 생성
        List<String> llmTextsToEmbed = pending.stream()
                .map(r -> (r.getTitle() == null ? "" : r.getTitle()) + " " + (r.getDescription() == null ? "" : r.getDescription()))
                .map(s -> s.replaceAll("\\s+", " ").trim())
                .toList();
        List<float[]> llmEmbeddings = embeddingService.embedAll(llmTextsToEmbed);

        for (int i = 0; i < pending.size(); i++) {
            var r = pending.get(i);
            Long tdsId = r.getTourDetailScheduleId();
            var list = results.getOrDefault(tdsId, List.of());
            if (list.isEmpty()) continue;

            successCount++;
            accumulateLearningData(learningDataToSave, list, llmTextsToEmbed.get(i), llmEmbeddings.get(i), true); // LLM 결과는 임계값 적용

            for (var res : list) {
                Long typeId = tstMapper.findScheduleTypeIdByName(res.typeName());
                if (typeId != null) {
                    tstMapper.upsertTourScheduleType(tdsId, typeId, res.score());
                }
            }
        }
        esService.saveLabel(learningDataToSave); // LLM 기반 학습 데이터 일괄 저장
        return successCount;
    }

    private void accumulateLearningData(List<LearningData> learningDataList, List<GenerativeLabelService.Result> results, String text, float[] embedding, boolean applyThreshold) {
        if (results == null || results.isEmpty()) {
            return;
        }

        Stream<GenerativeLabelService.Result> stream = results.stream();
        if (applyThreshold) {
            stream = stream.filter(r -> r.score() >= CONFIDENCE_THRESHOLD);
        }

        List<Map<String, Object>> labelsToSave = stream
                .map(r -> {
                    Map<String, Object> labelMap = new HashMap<>();
                    labelMap.put("name", r.typeName());
                    labelMap.put("weight", r.score());
                    return labelMap;
                })
                .collect(Collectors.toList());

        if (!labelsToSave.isEmpty()) {
            learningDataList.add(new LearningData(labelsToSave, text, embedding));
        }
    }

    private boolean isConflict(String a, String b) {
        if (a == null || b == null) return false;
        return (MOVE.contains(a) && VIEW.contains(b)) || (MOVE.contains(b) && VIEW.contains(a));
    }
}
