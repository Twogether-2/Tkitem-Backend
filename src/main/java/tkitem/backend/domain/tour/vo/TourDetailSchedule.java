package tkitem.backend.domain.tour.vo;

import lombok.*;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TourDetailSchedule {
    private Long tourDetailScheduleId;
    private Long tourId;
    private String title;
    private LocalDate scheduleDate;
    private String description;
    private int sortOrder;
}
