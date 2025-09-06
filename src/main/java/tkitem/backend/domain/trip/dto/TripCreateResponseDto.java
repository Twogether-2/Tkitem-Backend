package tkitem.backend.domain.trip.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TripCreateResponseDto {
    private Long tripId;
    private Long memberId;
    private Long tourPackageId;
    private String title;
    private Date departureDate;
    private Date arrivalDate;
    private String type;
    private String imgUrl;
    private Integer price;
}