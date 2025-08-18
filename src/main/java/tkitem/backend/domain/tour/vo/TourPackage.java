package tkitem.backend.domain.tour.vo;

import lombok.*;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TourPackage {
    private Long tourPackageId;
    private Long tourId;
    private int price;
    private LocalDate departureDate;
    private LocalDate returnDate;
    private String packageDateCode; // 현대트레블 travleId 값
}
