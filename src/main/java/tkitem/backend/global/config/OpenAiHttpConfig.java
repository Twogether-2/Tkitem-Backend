package tkitem.backend.global.config;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.apache.hc.client5.http.config.RequestConfig;

import java.time.Duration;

@Configuration
public class OpenAiHttpConfig {

    @Bean
    RestClientCustomizer openAiClientNoProxy() { // [추가]
        return builder -> {
            var cm = PoolingHttpClientConnectionManagerBuilder.create().build();

            // [추가] 타임아웃은 RequestConfig에 설정
            var rc = RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(5))            // TCP 연결
                    .setConnectionRequestTimeout(Timeout.ofSeconds(5))  // 커넥션풀에서 가져오기
                    .setResponseTimeout(Timeout.ofSeconds(20))          // 서버 응답 대기(= read timeout)
                    .build();

            var http = HttpClients.custom()
                    .setConnectionManager(cm)
                    .setDefaultRequestConfig(rc)            // [추가]
                    .disableAutomaticRetries()              // [추가] 재시도 끔(원인 파악 쉬움)
                    .evictExpiredConnections()
                    .evictIdleConnections(TimeValue.ofSeconds(30))
                    .build();

            // [변경] 팩토리 생성자에 httpClient 주입
            var f = new HttpComponentsClientHttpRequestFactory(http);
            f.setConnectTimeout(Duration.ofSeconds(5));            // (선택) 중복 설정 가능
            f.setConnectionRequestTimeout(Duration.ofSeconds(5));  // (선택)

            builder.requestFactory(f);
        };
    }
}