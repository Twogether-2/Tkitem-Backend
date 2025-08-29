package tkitem.backend.domain.scheduleType.service;

import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.scheduleType.classification.RuleClassifier;
import tkitem.backend.domain.scheduleType.dto.TourDetailScheduleRowDto;
import tkitem.backend.domain.scheduleType.mapper.TourDetailScheduleMapper;
import tkitem.backend.domain.scheduleType.mapper.TourScheduleTypeMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final int PENDING_FLUSH = 2000;
    private static final String ES_INDEX = "tour_detail_schedule_v1";

    // TODO :
    //  1. TDS 분류 시 DEFAULT_TYPE 을 보고 게산을 다르게 매겨야 함.
    //  MEAL 은 식사고,
    //  PLACE 는 단순 지역명이라 빼도되고,
    //  ACCOMMODATION 은 호텔이라 description 에 평점이 있고,
    //  COLLECTION 은 TOUR 중 대표 여행이라는 의미
    //  2. 함께가는 사람에 대한 분류도 고민할거리다.
    //  3. 적은 데이터로 분류 테스트 필요

    // 한번만 돌리는 메인 엔트리
    @Transactional
    public void runOnce(int batchSize) throws Exception {
        esService.ensureIndexExistsOrThrow();

        int offset = 0;
        List<TourDetailScheduleRowDto> knnTargets = new ArrayList<>(); // KNN 대상
        List<TourDetailScheduleRowDto> llmPending = new ArrayList<>(); // LLM 대상
        long lastProcessedId = -1; // 체크포인트(로그로만 남겨도 OK)
        int forTest = 10;
        int countTest = 0;

        while (countTest < forTest) {
            // 1) DB 배치 조회
            List<TourDetailScheduleRowDto> rows = tdsMapper.selectBatchForIndexing(offset, batchSize);
            if (rows == null || rows.isEmpty()) break;

            // 2) ES Bulk 색인 (임베딩 포함)
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            for (var r : rows) {

                String title = r.getTitle() == null ? "" : r.getTitle();
                String desc = r.getDescription() == null ? "" : r.getDescription();
                String combined = (title + " " +desc).replaceAll("\\s+", " ").trim();
                float[] vec = embeddingService.embed(combined);

                // ES 문서 전송 (대상 인덱스/별칭은 설정값)
                Map<String, Object> d = toEsDoc(r, title, desc, combined, vec);

                bulk.operations(op -> op.index(idx -> idx
                        .index(ES_INDEX)
                        .id(String.valueOf(r.getTourDetailScheduleId()))
                        .document(d)
                ));

                // defaultType 기반 분류 제어: PLACE는 분류 생략
                String dt = r.getDefaultType();
                if (dt != null && dt.equalsIgnoreCase("PLACE")) {
                    continue; // 분류 생략(TST 미적재), 다음 레코드로
                }

                // 3) 룰 1차 분류 → 확신 낮으면 pending
                var scores = rule.score(r.getTitle(), r.getDescription(), r.getDefaultType());
                var top = rule.top2(scores);
                if (top.top1Type != null) {
                    double top1 = top.top1Score;
                    double top2 = top.top2Type==null? 0.0 : top.top2Score;
                    double margin = top1 - top2;

                    if (top1 >= MIN_SCORE && margin >= MIN_MARGIN) {
                        // 4) 확신 충분 → DB UPSERT + ES 업데이트 예약
                        Long typeId = tstMapper.findScheduleTypeIdByName(top.top1Type);
                        if (typeId != null) {
                            tstMapper.upsertTourScheduleType(r.getTourDetailScheduleId(), typeId, top1);
                        }
                    } else {
                        knnTargets.add(r); // 생성형 분류로 넘길 대기 목록
                    }
                }

                lastProcessedId = r.getTourDetailScheduleId(); // 체크포인트 갱신
            }

            BulkResponse resp = esService.bulk(bulk.build()); // 대량 색인/업데이트 실행

            // 4) KNN Top-3 저장 -> 미달 항목은 LLM 보완 대상으로 이동
            if(!knnTargets.isEmpty()){
                Map<Long, List<GenerativeLabelService.Result>> knnMap = genSvc.classifyBatchByKNN(knnTargets);

                for (var r : knnTargets) {
                    Long tdsId = r.getTourDetailScheduleId();
                    var list = knnMap.getOrDefault(tdsId, List.of());

                    int saved = 0;
                    for (var res : list) {
                        Long typeId = tstMapper.findScheduleTypeIdByName(res.typeName());
                        if (typeId == null) {
                            log.warn("알 수 없는 타입(KNN): {} (tdsId={})", res.typeName(), tdsId);
                            continue;
                        }
                        tstMapper.upsertTourScheduleType(tdsId, typeId, res.score());
                        if (++saved >= 3) break; //  최대 3개까지
                    }

                    // top1 미달 또는 KNN 결과 없음 → LLM 보완
                    if (list.isEmpty() || list.get(0).score() < MIN_SCORE) {
                        llmPending.add(r);
                    }
                }
                knnTargets.clear();
            }

            // 5) pending 임계치 도달 시 즉시 생성형 분류 → DB/ES 반영
            if (llmPending.size() >= PENDING_FLUSH) {
                flushPendingWithGen(llmPending);
                llmPending.clear();
            }

            offset += rows.size();
            countTest += rows.size();
            log.info("Indexed+scored batch: {} (lastId={})", rows.size(), lastProcessedId);
        }

        // 6) 남은 llmPending 최종 플러시
        if (!llmPending.isEmpty()) flushPendingWithGen(llmPending);
        log.info("pipeline completed");
    }

    // ES 문서 변환
    private Map<String, Object> toEsDoc(TourDetailScheduleRowDto r, String title, String desc, String combined, float[] vec){

        Map<String, Object> doc = new HashMap<>();
        doc.put("tour_detail_schedule_id", r.getTourDetailScheduleId());
        doc.put("tour_id", r.getTourId());
        doc.put("schedule_date", r.getScheduleDate());
        doc.put("sort_order", r.getSortOrder());
        doc.put("country_name", r.getCountryName());
        doc.put("city_name", r.getCityName());
        doc.put("title", title);
        doc.put("description", desc);
        doc.put("combined_text", combined);
        doc.put("embedding", vec);

        return doc;
    }

    // KNN 분류 -> 부족 시 LLM 분류 -> 라벨 임베딩 → DB 반영
    private void flushPendingWithGen(List<TourDetailScheduleRowDto> pending) throws Exception {

        if (pending == null || pending.isEmpty()) return;

        // LLM 보조 분류 (배치 호출)
        Map<Long, List<GenerativeLabelService.Result>> results = genSvc.classifyBatchByLLM(pending);

        for (var r : pending) {
            Long tdsId = r.getTourDetailScheduleId();
            var list = results.getOrDefault(tdsId, List.of());
            if (list.isEmpty()) continue;

            int saved = 0;
            for(var res : list){
                Long typeId = tstMapper.findScheduleTypeIdByName(res.typeName());
                if (typeId == null) {
                    log.warn("알 수 없는 타입(LLM 분류중): {} (tdsId={})", res.typeName(), tdsId);
                    continue;
                }
                tstMapper.upsertTourScheduleType(tdsId, typeId, res.score());
                if (++saved >= 3) break;
            }
        }
    }

}