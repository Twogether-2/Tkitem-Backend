package tkitem.backend.domain.gemini.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gemini 임베딩 REST 클라이언트 (Spring WebClient)
 * - endpoint: https://generativelanguage.googleapis.com/v1beta/models/{model}:embedContent
 * - query: outputDimensionality(옵션)
 * - QPS 제한, 지수 백오프 재시도 포함
 */
@Service
public class GeminiEmbeddingService {

    private final WebClient webClient;
    private final String modelName;
    private final Integer outputDimensionality;        // (null이면 기본 차원)
    private final int maxQps;                          // 초당 요청 제한

    // 간단한 1초 윈도우 QPS 리미터
    private final AtomicLong windowStartMs = new AtomicLong(0);
    private final AtomicInteger usedInWindow = new AtomicInteger(0);

    public GeminiEmbeddingService(
                                   @Value("${gemini.api-key}") String apiKey,
                                   @Value("${gemini.embedding-model:gemini-embedding-001}") String modelName,
                                   @Value("${gemini.timeout-ms:60000}") long timeoutMs,
                                   @Value("${gemini.max-qps:10}") int maxQps,
                                   @Value("${gemini.output-dimensionality:0}") int outputDimensionality // 0이면 미사용
    ) {
        this.modelName = modelName;
        this.maxQps = Math.max(1, maxQps);
        this.outputDimensionality = (outputDimensionality > 0 ? outputDimensionality : null);

        this.webClient = WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", apiKey)
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)) // 16MB
                .build();
    }

    /**
     *  단일 텍스트 임베딩
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) return new float[0];
        rateLimit(); // QPS 제한

        Map<String, Object> body = Map.of(
                "content", Map.of("parts", List.of(Map.of("text", text)))
        );

        // 재시도 래퍼
        return withRetry(() ->
                webClient.post()
                        .uri(builder -> {
                            builder.path("/models/" + modelName + ":embedContent");
                            if (outputDimensionality != null) {
                                builder.queryParam("outputDimensionality", outputDimensionality);
                            }
                            return builder.build();
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofMillis(60_000))
                        .map(resp -> parseEmbedding(resp))
                        .block()
        );
    }

    /**
     * [신규] 배치 임베딩(간단 분할). 대량일수록 batchSize를 낮춰 QPS/타임아웃 관리
     */
    public List<float[]> embedBatch(List<String> texts, int batchSize) {
        List<float[]> out = new ArrayList<>(texts.size());
        int n = texts.size();
        for (int i = 0; i < n; i += batchSize) {
            int end = Math.min(i + batchSize, n);
            var slice = texts.subList(i, end);
            for (String s : slice) out.add(embed(s));
        }
        return out;
    }

    // ==========================
    // 내부 유틸
    // ==========================

    // 응답 파싱: {"embedding":{"values":[..]}}
    @SuppressWarnings("unchecked")
    private float[] parseEmbedding(Map resp) {
        Object embObj = resp.get("embedding");
        if (!(embObj instanceof Map)) return new float[0];
        Object values = ((Map<?, ?>) embObj).get("values");
        if (!(values instanceof List<?> list)) return new float[0];

        float[] vec = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object v = list.get(i);
            if (v instanceof Number num) vec[i] = num.floatValue();
        }
        return vec;
    }

    // 간단 QPS 제한(1초 윈도우)
    private void rateLimit() {
        long now = System.currentTimeMillis();
        long start = windowStartMs.get();
        if (now - start >= 1000) {
            // 새 윈도우 시작
            windowStartMs.set(now);
            usedInWindow.set(0);
        }
        // 남은 토큰 없으면 다음 윈도우까지 슬립
        int used = usedInWindow.incrementAndGet();
        if (used > maxQps) {
            long sleep = 1000 - (now - windowStartMs.get());
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
            }
            windowStartMs.set(System.currentTimeMillis());
            usedInWindow.set(1);
        }
    }

    //  429/5xx 재시도(지수 백오프). 4xx(429 제외)는 즉시 실패.
    private <T> T withRetry(SupplierEx<T> call) {
        int maxAttempts = 5;
        long backoff = 200; // ms
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (WebClientResponseException e) {
                int status = e.getRawStatusCode();
                boolean retryable = (status == 429) || (status >= 500);
                if (!retryable || attempt == maxAttempts) throw e;
            } catch (Exception e) {
                if (attempt == maxAttempts) throw new RuntimeException(e);
            }
            try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
            backoff = Math.min(backoff * 2, 3000); // 최대 3s
        }
        throw new IllegalStateException("Unreachable");
    }

    @FunctionalInterface
    private interface SupplierEx<T> {
        T get() throws Exception;
    }
}