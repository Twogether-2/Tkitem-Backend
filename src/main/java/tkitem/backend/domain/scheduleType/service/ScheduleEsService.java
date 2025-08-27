package tkitem.backend.domain.scheduleType.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.KnnSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleEsService {

    private static final String INDEX = "tour_detail_schedule_v1";
    private final ElasticsearchClient esClient;

    public BulkResponse bulk(BulkRequest request) throws IOException {
        BulkResponse resp = esClient.bulk(request);
        if (resp.errors()) {
            // 실패 항목 로깅(요약)
            resp.items().stream()
                    .filter(it -> it.error() != null)
                    .limit(5)
                    .forEach(it -> log.error("Bulk fail: op={} id={} reason={}",
                            it.operationType(), it.id(), it.error().reason()));
        }
        return resp;
    }

    /**
     * 인덱스 존재 확인 유틸 메서드
     * @throws IOException
     */
    public void ensureIndexExistsOrThrow() throws IOException {
        boolean exists = esClient.indices().exists(b -> b.index(INDEX)).value();
        if (!exists) {
            throw new IllegalStateException("Elasticsearch index not found: " + INDEX);
        }
    }

    /**
     * KNN + 필터 오버로드
     * @param queryVector
     * @param k
     * @param candidates
     * @param countryName
     * @param cityName
     * @param tourId
     * @return
     * @throws Exception
     */
    public KnnSearchResponse<Map> knnWithFilter(
            float[] queryVector, int k, int candidates,
            String countryName, String cityName, Long tourId
    ) throws Exception {

        List<Float> qv = new ArrayList<>(queryVector.length);
        for (float v : queryVector) qv.add(v);

        // 지역 필터 설정
        List<Query> filters = new ArrayList<>();
        if (countryName != null && !countryName.isBlank()) {
            filters.add(Query.of(q -> q.term(t -> t.field("country_name").value(countryName))));
        }
        if (cityName != null && !cityName.isBlank()) {
            filters.add(Query.of(q -> q.term(t -> t.field("city_name").value(cityName))));
        }
        if (tourId != null) {
            filters.add(Query.of(q -> q.term(t -> t.field("tour_id").value(tourId))));
        }

        // 예산 필터 설정 필요

        // 여행 일정 필터 설정 필요

        KnnSearchResponse<Map> resp = esClient.knnSearch(r -> r
                        .index(INDEX)
                        .knn(kn -> kn
                                .field("embedding")
                                .queryVector(qv)
                                .k(k)
                                .numCandidates(candidates)
                        )
                        .filter(filters) // [추가] 선택 필터 적용
                        .source(src -> src.filter(f -> f
                                .includes("tour_detail_schedule_id","tour_id","title","city_name","country_name","schedule_date")))
                , Map.class);

        return resp;
    }

}