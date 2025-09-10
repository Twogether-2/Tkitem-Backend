package tkitem.backend.domain.product_recommendation.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Preference {
    private Long preferenceId;
    private Long memberId;
    private Integer brightness;
    private Integer boldness;
    private Integer fit;
    private Integer color;
    private String firstLook;
    private String secondLook;
}