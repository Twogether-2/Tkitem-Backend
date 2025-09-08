package tkitem.backend.domain.tour.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TourCandidateRowDto {
    private Long tourId;
    private Double sDbRaw; // tour_style 가중치 점수
    private Integer tdsCnt;
//    private Long minPrice;
//    private Date latestDeparture;

    private Long repTourPackageId;
    private Long repPrice;
    private Date repDepartureDate;
    private Date repReturnDate;
    private String repBookingUrl;
    private String repDepartureAirline;
    private String repReturnAirline;
}
