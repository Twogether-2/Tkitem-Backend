package tkitem.backend.domain.tour.dto.response;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourPackageDto {
    private Long tourId;
    private Long tourPackageId;
    private Long price;
    private Date departureDate;
    private Date returnDate;
    private String bookingUrl;
    private String departureAirline;
    private String returnAirline;
}
