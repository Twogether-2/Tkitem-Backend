package tkitem.backend.global.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticConfig {

    @Value("${elastic.host:localhost}") private String host;   // [추가]
    @Value("${elastic.port:9200}")     private int port;       // [추가]
    @Value("${elastic.scheme:http}")   private String scheme;

    @Bean
    public RestClient restClient() {
        return RestClient.builder(new HttpHost(host, port, scheme)).build();
    }
    // TODO : 보안 추가시 xpack.security 계정/비번/SSL 설정 추가 필요
    @Bean
    public ElasticsearchClient esClient(RestClient rc) {
        RestClientTransport transport = new RestClientTransport(rc, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
