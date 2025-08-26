package tkitem.backend.domain.scheduleType.service;

import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.scheduleType.classification.RuleClassifier;
import tkitem.backend.domain.scheduleType.dto.ScheduleEsDocumentDto;
import tkitem.backend.domain.scheduleType.dto.TourDetailScheduleRowDto;
import tkitem.backend.domain.scheduleType.mapper.TourDetailScheduleMapper;
import tkitem.backend.domain.scheduleType.mapper.TourScheduleTypeMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TourTypePipelineService {
    private final TourDetailScheduleMapper tdsMapper;
    private final ScheduleEsService esService;
    private final EmbeddingService embeddingService;
    private final RuleClassifier rule = new RuleClassifier();
    private final TourScheduleTypeMapper tstMapper;
    private final GenerativeLabelService genSvc;

    private static final double MIN_SCORE = 0.65;
    private static final double MIN_MARGIN = 0.10;
    private static final int PENDING_FLUSH = 2000;
    private static final String ES_INDEX = "tour_detail_schedule_v1";

    // 한번만 돌리는 메인 엔트리
    @Transactional
    public void runOnce(int batchSize) throws Exception {
        int offset = 0;
        List<TourDetailScheduleRowDto> pending = new ArrayList<>();
        long lastProcessedId = -1; // 체크포인트(로그로만 남겨도 OK)
        int forTest = 10;
        int countTest = 0;

        while (countTest < forTest) {
            // 1) DB 배치 조회
            var rows = tdsMapper.selectBatchForIndexing(offset, batchSize);
            if (rows == null || rows.isEmpty()) break;

            // 2) ES Bulk 색인 (임베딩 포함)
            BulkRequest.Builder bulk = new BulkRequest.Builder();
            for (var r : rows) {
                String text = ((r.getTitle()==null?"":r.getTitle()) + " " +
                        (r.getDescription()==null?"":r.getDescription())).trim();
                float[] vec = embeddingService.embed(text);

                // ES 문서 전송 (대상 인덱스/별칭은 설정값)
                ScheduleEsDocumentDto d = toEsDoc(r, vec);
                bulk.operations(op -> op.index(idx -> idx
                        .index(ES_INDEX)
                        .id(String.valueOf(d.getTourDetailScheduleId()))
                        .document(d)
                ));

                // 3) 룰 1차 분류 → 확신 낮으면 pending
                var scores = rule.score(r.getTitle(), r.getDescription(), null);
                var top = rule.top2(scores);
                if (top.top1Type != null) {
                    double top1 = top.top1Score;
                    double top2 = top.top2Type==null? 0.0 : top.top2Score;
                    double margin = top1 - top2;

                    if (top1 >= MIN_SCORE && margin >= MIN_MARGIN) {
                        // 4) 확신 충분 → DB UPSERT + ES 업데이트 예약
                        Long typeId = tstMapper.findScheduleTypeIdByName(top.top1Type);
                        if (typeId != null) {
                            tstMapper.upsertTourScheduleType(Long.valueOf(r.getTourDetailScheduleId()), typeId, top1);
                            // ES 도큐먼트에 분류값도 저장하고 싶으면 업데이트 문서 준비
                            bulk.operations(op -> op.update(u -> u
                                    .index(ES_INDEX)
                                    .id(String.valueOf(r.getTourDetailScheduleId()))
                                    .action(a -> a.doc(Map.of(
                                            "schedule_type", top.top1Type,
                                            "schedule_type_score", top1
                                    ))
                                            .docAsUpsert(false) // upSert 불필요 하면 명시
                                )
                            ));
                        }
                    } else {
                        pending.add(r); // 생성형 분류로 넘길 대기 목록
                    }
                }

                lastProcessedId = r.getTourDetailScheduleId(); // 체크포인트 갱신
            }

            BulkResponse resp = esService.bulk(bulk.build()); // 대량 색인/업데이트 실행

            // 5) pending 임계치 도달 시 즉시 생성형 분류 → DB/ES 반영
            if (pending.size() >= PENDING_FLUSH) {
                flushPendingWithGen(pending);
                pending.clear();
            }

            offset += rows.size();
            // [로그] lastProcessedId 로 진행 상황 남기기
            countTest += rows.size();
        }

        // 6) 남은 pending 최종 플러시
        if (!pending.isEmpty()) flushPendingWithGen(pending);
    }

    // ES 문서 변환
    private ScheduleEsDocumentDto toEsDoc(TourDetailScheduleRowDto r, float[] vec){
        ScheduleEsDocumentDto d = ScheduleEsDocumentDto.builder()
                .tourDetailScheduleId(r.getTourDetailScheduleId())
                .tourId(r.getTourId())
                .scheduleDate(r.getScheduleDate())
                .countryName(r.getCountryName())
                .cityName(r.getCityName())
                .title(r.getTitle())
                .description(r.getDescription())
                .embedding(vec)
                .build();
        return d;
    }

    // 생성형 분류 → DB/ES 반영
    private void flushPendingWithGen(List<TourDetailScheduleRowDto> pending) throws Exception {

        if (pending == null || pending.isEmpty()) return;

        // 1) LLM 보조 분류 (배치 호출)
        Map<Long, GenerativeLabelService.Result> results = genSvc.classifyBatch(pending);

        // 2) DB UPSERT + ES Bulk Update
        BulkRequest.Builder br = new BulkRequest.Builder();
        int opCount = 0; // 실제 update 개수 count

        for (var r : pending) {
            Long tdsId = r.getTourDetailScheduleId();
            GenerativeLabelService.Result res = results.get(tdsId);
            if (res == null) continue;

            Long typeId = tstMapper.findScheduleTypeIdByName(res.typeName());
            if(typeId == null && res.typeName() != null){
                log.warn("알려지지 않은 스케줄 타입이 들어왔음: {}", res.typeName());
                continue;
            }

            tstMapper.upsertTourScheduleType(tdsId, typeId, res.score());

            br.operations(op -> op.update(u -> u
                    .index(ES_INDEX)
                    .id(String.valueOf(r.getTourDetailScheduleId()))
                    .action(a -> {
                        assert res.typeName() != null;
                        return a.doc(Map.of(
                                "schedule_type", res.typeName(),
                                "schedule_type_score", res.score()
                        ))
                                .docAsUpsert(false);
                    } // 신규 upsert 는 하지 않음
                )
            ));
            opCount++;
        }

        // 실제 업데이트가 있을 때에만 bulk 실행
        if (opCount > 0) {
            BulkResponse resp = esService.bulk(br.build());
            if (resp.errors()) {
                resp.items().stream()
                        .filter(it -> it.error() != null)
                        .limit(5)
                        .forEach(it -> log.error("Bulk fail: op={} id={} reason={}",
                                it.operationType(), it.id(), it.error().reason()));
            }
        }
    }
}