package tkitem.backend.domain.checklist.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistAiResponseDto {
    private Long tripId;
    private int itemCount;
}
