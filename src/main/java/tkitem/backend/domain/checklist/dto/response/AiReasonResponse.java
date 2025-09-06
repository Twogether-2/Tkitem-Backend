package tkitem.backend.domain.checklist.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class AiReasonResponse {
    private String headline;
    private List<Section> sections;

    @Data
    public static class Section {
        private String title;
        private List<String> paragraphs;
    }
}
