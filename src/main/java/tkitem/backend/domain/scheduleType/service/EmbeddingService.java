package tkitem.backend.domain.scheduleType.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingModel embeddingModel;

    /**
     * 임베딩 생성
     * @param text
     * @return
     */
    public float[] embed(String text){
        EmbeddingRequest req = new EmbeddingRequest(
                List.of(text),
                OpenAiEmbeddingOptions.builder().model("text-embedding-3-small").build()
        );

        EmbeddingResponse res = embeddingModel.call(req);

        return res.getResults().get(0).getOutput();
    }


}
