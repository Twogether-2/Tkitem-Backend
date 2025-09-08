package tkitem.backend.global.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class ElasticConfig {

    @Value("${elastic.host:localhost}") private String host;
    @Value("${elastic.port:9200}") private int port;
    @Value("${elastic.scheme:http}") private String scheme;

    @Value("${elastic.endpoint:}") private String endpoint;
    @Value("${elastic.apiKey:}") private String apiKey;

    private final Environment env;
    public ElasticConfig(Environment env) { this.env = env; }

    @Bean
    public ElasticsearchClient elasticsearchClient(org.elasticsearch.client.RestClient rest) {
        ElasticsearchTransport transport = new RestClientTransport(rest, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    @Bean
    public RestClient lowLevelRestClient() {
        String ep = notBlank(endpoint)
                ? endpoint
                : orEnv("ELASTICSEARCH_HOST", buildFromParts(scheme, host, port));
        HttpHost httpHost = HttpHost.create(ep);
        String key = notBlank(apiKey) ? apiKey : env.getProperty("ELASTIC_API_KEY", "");

        Header[] headers = notBlank(key)
                ? new Header[]{ new BasicHeader("Authorization", "ApiKey " + key) }
                : new Header[0];
        return RestClient.builder(httpHost)
                .setDefaultHeaders(headers)
                .build();
    }

    private String orEnv(String name, String fallback) {
        String v = env.getProperty(name);
        return notBlank(v) ? v : fallback;
    }
    private String buildFromParts(String sch, String h, int p) {
        if (notBlank(h) && p > 0) return sch + "://" + h + ":" + p;
        throw new IllegalStateException("Elasticsearch endpoint가 설정되지 않았습니다.");
    }
    private boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }
}
