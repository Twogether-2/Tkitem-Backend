package tkitem.backend.domain.scheduleType.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.KnnSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.scheduleType.dto.ScheduleEsDocumentDto;
import tkitem.backend.domain.scheduleType.dto.TourDetailScheduleRowDto;
import tkitem.backend.domain.scheduleType.mapper.TourDetailScheduleMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleEsService {

    private final ElasticsearchClient es;

    private static final String INDEX = "tour_detail_schedule_v1";
    private final TourDetailScheduleMapper tourDetailScheduleMapper;
    private final EmbeddingService embeddingService;
    private final ElasticsearchClient esClient;

    /**
     * DTO -> 맵 변환 후 ES 인덱스에 단건 색인 저장.
     * @param d
     * @throws Exception
     */
    public void index(ScheduleEsDocumentDto d)throws Exception{
        Map<String, Object> doc = new HashMap<>();
        doc.put("tour_detail_schedule_id", d.getTourDetailScheduleId());
        doc.put("tour_id", d.getTourId());
        doc.put("schedule_date", d.getScheduleDate());
        doc.put("country_name", d.getCountryName());
        doc.put("city_name", d.getCityName());
        doc.put("title", d.getTitle());
        doc.put("description", d.getDescription());
        doc.put("embedding", d.getEmbedding());

        IndexResponse response = es.index(i -> i
                .index(INDEX)
                .id(d.getTourDetailScheduleId()) // id 지정으로 중복 방지
                .document(doc));
    }

    /**
     * ES 의 _knn_search 로 임베딩 유사도 Top-K 문서 찾아 반환
     * @param queryVector
     * @param k
     * @param candidates
     * @return
     * @throws Exception
     */
    public KnnSearchResponse<Map> knn(float[] queryVector, int k, int candidates) throws Exception{

        List<Float> qv = new ArrayList<>(queryVector.length);
        for (float v : queryVector) qv.add(v);

        KnnSearchResponse<Map> resp = es.knnSearch(r -> r
                        .index(INDEX)
                        .knn(kn -> kn
                                .field("embedding")
                                .queryVector(qv)
                                .k(k)
                                .numCandidates(candidates)
                        )
                        .source(src -> src.filter(f -> f
                                .includes("tour_detail_schedule_id","tour_id","title","city_name","schedule_date")))
                , Map.class);

        return resp;
    }

    /**
     * DB에서 일정 조회 -> 다량 배치 임베딩 생성 -> ES 전송
     * @param offset
     * @param limit
     * @return
     * @throws Exception
     */
    @Transactional(readOnly = true)
    public int bulkIndex(int offset, int limit) throws Exception {
        List<TourDetailScheduleRowDto> rows = tourDetailScheduleMapper.selectBatchForIndexing(offset, limit);
        if (rows == null || rows.isEmpty()) return 0;

        BulkRequest.Builder br = new BulkRequest.Builder();

        for (TourDetailScheduleRowDto r : rows) {
            //  임베딩 생성 (title+description)
            String text = (r.getTitle() == null ? "" : r.getTitle()) + " " + (r.getDescription() == null ? "" : r.getDescription());
            float[] vec = embeddingService.embed(text);

            // ES 도큐먼트 변환
            ScheduleEsDocumentDto doc = ScheduleEsDocumentDto.builder()
                    .tourDetailScheduleId(String.valueOf(r.getTourDetailScheduleId()))
                    .tourId(String.valueOf(r.getTourId()))
                    .scheduleDate(r.getScheduleDate())
                    .countryName(r.getCountryName())
                    .cityName(r.getCityName())
                    .title(r.getTitle())
                    .description(r.getDescription())
                    .embedding(vec)
                    .build();

            br.operations(op -> op.index(idx -> idx
                    .index("tour_detail_schedule_v1") // 필요 시 별칭으로 교체
                    .id(doc.getTourDetailScheduleId()) // 멱등 보장
                    .document(doc)
            ));
        }

        // ES 에 구성한 dto 보내기
        BulkResponse resp = esClient.bulk(br.build());
        if (resp.errors()) {
            // 실패 로그
            resp.items().forEach(item -> {
                if (item.error() != null) {
                    log.error("ES bulk fail id={}, type={}, reason={}",
                            item.index(), item.error().type(), item.error().reason());
                }
            });
        }
        return rows.size();
    }
}