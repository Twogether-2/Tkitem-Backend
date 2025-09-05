package tkitem.backend.domain.scheduleType.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleEsService {

    private static final String INDEX = "tour_detail_schedule_v1";
    private static final String LABEL_INDEX = "schedule_type_labels_v1";
    private final ElasticsearchClient esClient;

    /**
     * tour_detail_schedule_v1 에 대량 색인 진행
     * @param request
     * @return
     * @throws IOException
     */
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
     * schedule_type_labels_v1 인덱스 존재 확인 (seeding 전용)
     * @return true if the index exists, false otherwise
     * @throws IOException
     */
    public boolean checkLabelIndexExists() throws IOException {
        return esClient.indices().exists(b -> b.index(LABEL_INDEX)).value();
    }

    /**
     * 신뢰도 높은 분류 결과를 라벨 인덱스에 저장 (다중 레이블 지원)
     */
    public void saveLabel(List<Map<String, Object>> labels, String text, float[] embedding) {
        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put("label", labels);
            doc.put("text", text);
            doc.put("embedding", embedding);

            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                    .index(LABEL_INDEX)
                    .document(doc)
            );
            esClient.index(request);
        } catch (IOException e) {
            log.error("Failed to save labels to Elasticsearch", e);
        }
    }
}