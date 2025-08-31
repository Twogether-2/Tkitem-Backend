package tkitem.backend.domain.scheduleType.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleEsService {

    private static final String INDEX = "tour_detail_schedule_v1";
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
}