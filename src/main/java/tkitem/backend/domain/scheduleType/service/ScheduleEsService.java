package tkitem.backend.domain.scheduleType.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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

        KnnSearchResponse<Map> resp = esClient.knnSearch(r -> r
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
}