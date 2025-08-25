package tkitem.backend.domain.scheduleType.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    @Autowired
    private org.springframework.core.env.Environment env;

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
        log.info("[OPENAI] base-url={}", env.getProperty("spring.ai.openai.base-url"));  // ← 이걸로 확인
        log.info("[OPENAI] api-key startsWith=sk-{}",
                Optional.ofNullable(env.getProperty("spring.ai.openai.api-key"))
                        .map(s -> s.startsWith("sk-")).orElse(false));
        EmbeddingResponse res = embeddingModel.call(req);

        return res.getResults().get(0).getOutput();
    }


}
