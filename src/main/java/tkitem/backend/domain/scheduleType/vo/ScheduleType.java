package tkitem.backend.domain.scheduleType.vo;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ScheduleType {
    private Long scheduleTypeId;
    private String name;
    private String nameKr;
}
