package tkitem.backend.domain.tour.dto.response;

import lombok.*;
import tkitem.backend.domain.tour.dto.TourDetailScheduleDto;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourPackageDetailDto {
    // 투어 정보
    private Long tourId;
    private String title;
    private String feature;
    private String imgUrl;
    private String provider;
    private Long tourPackageId;

    // 투어 일자 정보
    List<TourPackageDto> packageDtos;

    // 투어 세부 일정 정보
    List<TourDetailScheduleDto> schedules;
}
