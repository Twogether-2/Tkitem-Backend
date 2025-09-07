package tkitem.backend.domain.scheduleType.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class ExemplarDto {
    private final List<LabelWeight> label;
    private final String text;

    @JsonCreator
    public ExemplarDto(@JsonProperty("label") List<LabelWeight> label, @JsonProperty("text") String text) {
        this.label = label;
        this.text = text;
    }

    @Getter
    public static class LabelWeight {
        private final String name;
        private final float weight;

        @JsonCreator
        public LabelWeight(@JsonProperty("name") String name, @JsonProperty("weight") float weight) {
            this.name = name;
            this.weight = weight;
        }
    }
}
