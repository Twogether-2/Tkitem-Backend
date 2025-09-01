package tkitem.backend.domain.scheduleType.test;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmbeddingSmokeTest implements CommandLineRunner {
    private final EmbeddingModel model;

    @Override
    public void run(String... args) {
        float[] embedding = model.embed("test");
        System.out.println("embedding dims=" + embedding.length); // 1536 나오면 정상
    }
}
