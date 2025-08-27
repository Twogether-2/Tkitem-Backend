package tkitem.backend.domain.checklist.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ChecklistCreateRequestDto {
    @NotNull
    private List<Long> productCategorySubIds;
    private Integer scheduleDate;
}
