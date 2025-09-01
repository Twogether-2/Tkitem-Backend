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
    RestClientCustomizer openAiClientNoProxy() {
        return builder -> {
            var cm = PoolingHttpClientConnectionManagerBuilder.create().build();

            var rc = RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(5))            // TCP 연결
                    .setConnectionRequestTimeout(Timeout.ofSeconds(5))  // 커넥션풀에서 가져오기
                    .setResponseTimeout(Timeout.ofSeconds(20))          // 서버 응답 대기(= read timeout)
                    .build();

            var http = HttpClients.custom()
                    .setConnectionManager(cm)
                    .setDefaultRequestConfig(rc)
                    .disableAutomaticRetries()
                    .evictExpiredConnections()
                    .evictIdleConnections(TimeValue.ofSeconds(30))
                    .build();

            // 팩토리 생성자에 httpClient 주입
            var f = new HttpComponentsClientHttpRequestFactory(http);
            f.setConnectTimeout(Duration.ofSeconds(5));
            f.setConnectionRequestTimeout(Duration.ofSeconds(5));

            builder.requestFactory(f);
        };
    }
}