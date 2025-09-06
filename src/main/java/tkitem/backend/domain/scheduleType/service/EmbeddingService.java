package tkitem.backend.domain.scheduleType.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * 임베딩 생성
     * @param text
     * @return
     */
    public float[] embed(String text){
        if(text == null) text = "";
        EmbeddingRequest req = new EmbeddingRequest(
                List.of(text),
                OpenAiEmbeddingOptions.builder().model("text-embedding-3-small").build()
        );
        EmbeddingResponse res = embeddingModel.call(req);
        return res.getResults().get(0).getOutput();
    }

    /**
     * 임베딩 생성 (배치)
     * @param texts
     * @return
     */
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        // API는 null 대신 빈 문자열을 선호
        List<String> nonEmptyTexts = texts.stream().map(t -> t == null ? "" : t).toList();

        EmbeddingRequest req = new EmbeddingRequest(
                nonEmptyTexts,
                OpenAiEmbeddingOptions.builder().model("text-embedding-3-small").build()
        );

        EmbeddingResponse res = embeddingModel.call(req);
        return res.getResults().stream().map(r -> r.getOutput()).toList();
    }
}