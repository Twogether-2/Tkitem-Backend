package tkitem.backend.domain.tour.vo;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TourDetailSchedule {
    private Long tourDetailScheduleId;
    private Long tourId;
    private Long cityId;
    private String title;
    private Integer scheduleDate;
    private String description;
    private int sortOrder;
    private String defaultType;
}
