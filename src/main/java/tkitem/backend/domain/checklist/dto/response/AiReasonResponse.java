package tkitem.backend.domain.checklist.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiReasonResponse(
        String headline,
        List<Section> sections
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static record Section(String title, List<String> paragraphs) {}
}
