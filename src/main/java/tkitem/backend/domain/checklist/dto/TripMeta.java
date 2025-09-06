package tkitem.backend.domain.checklist.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TripMeta {
    private Integer monthNum;  // 1~12
    private Integer tripDays;  // 일정 일수
}
