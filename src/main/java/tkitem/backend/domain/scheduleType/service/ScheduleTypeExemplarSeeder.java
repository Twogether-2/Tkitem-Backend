package tkitem.backend.domain.scheduleType.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tkitem.backend.domain.scheduleType.dto.ExemplarDto;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleTypeExemplarSeeder {

    private final ElasticsearchClient esClient;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final ScheduleEsService esService;

    private static final String LABEL_INDEX = "schedule_type_labels_v1";
    private static final String EXEMPLARS_PATH = "dummy/schedule_type_exemplars.json";

    public void seedExemplars() throws Exception {
        log.info("Exemplar seeding process started for index: {}", LABEL_INDEX);

        // 1. Check if the target index exists. If not, stop the process.
        if (!esService.checkLabelIndexExists()) {
            log.error("Index {} does not exist. Please create it via Kibana Dev Tools before running the seeder.", LABEL_INDEX);
            return; // Stop the process
        }
        log.info("Successfully verified that index {} exists.", LABEL_INDEX);

        // 2. Load exemplars from JSON file
        ClassPathResource resource = new ClassPathResource(EXEMPLARS_PATH);
        InputStream inputStream = resource.getInputStream();
        List<ExemplarDto> exemplars = objectMapper.readValue(inputStream, new TypeReference<>() {});
        log.info("Loaded {} exemplars from {}", exemplars.size(), EXEMPLARS_PATH);

        // 3. Create embeddings and prepare for bulk indexing
        BulkRequest.Builder bulk = new BulkRequest.Builder();
        int count = 0;
        for (ExemplarDto exemplar : exemplars) {
            String text = exemplar.getText();
            List<ExemplarDto.LabelWeight> labels = exemplar.getLabel();

            if (text == null || text.isBlank() || labels == null || labels.isEmpty()) {
                continue;
            }

            // Generate embedding for the text
            float[] embedding = embeddingService.embed(text);

            // Prepare document for Elasticsearch, including the nested structure for labels
            Map<String, Object> doc = new HashMap<>();
            List<Map<String, Object>> nestedLabels = new ArrayList<>();
            for (ExemplarDto.LabelWeight lw : labels) {
                Map<String, Object> labelMap = new HashMap<>();
                labelMap.put("name", lw.getName());
                labelMap.put("weight", lw.getWeight());
                nestedLabels.add(labelMap);
            }
            doc.put("label", nestedLabels);
            doc.put("text", text);
            doc.put("embedding", embedding);

            int docId = ++count;
            bulk.operations(op -> op.index(idx -> idx
                    .index(LABEL_INDEX)
                    .id(String.valueOf(docId))
                    .document(doc)
            ));
        }

        // 4. Execute bulk indexing using the existing generic bulk method
        if (count > 0) {
            log.info("Indexing {} documents into {}...", count, LABEL_INDEX);
            esService.bulk(bulk.build());
            log.info("Successfully indexed {} documents.", count);
        } else {
            log.warn("No valid exemplars found to index.");
        }

        log.info("Exemplar seeding process finished.");
    }
}
