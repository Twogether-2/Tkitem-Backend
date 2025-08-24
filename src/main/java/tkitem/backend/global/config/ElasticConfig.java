package tkitem.backend.global.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticConfig {

    // TODO : 보안 추가시 xpack.security 계정/비번/SSL 설정 추가 필요
    @Bean
    public ElasticsearchClient esClient(@Value("${elasticsearch.host}") String host) {
        RestClientBuilder builder = RestClient.builder(org.apache.http.HttpHost.create(host));
        RestClient restClient = builder.build();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
