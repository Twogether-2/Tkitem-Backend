package tkitem.backend.domain.trip.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpcomingTripResponse {
    private Long tripId;
    private String title;
    private String departureDate;
    private String arrivalDate;
    private String imgUrl;
    private String country;
    private String cities;
    private int dDay;
}

