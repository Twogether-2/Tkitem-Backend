package tkitem.backend.domain.tour.dto;

import lombok.*;
import java.util.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class KeywordRule {
    private String keyword;
    private String country;         // 단일 국가
    private String countryGroup;    // 국가그룹(예: 동남아, 유럽)

    // JSON의 오타(sholudList, excloudList)까지 허용하기 위해 로더에서 매핑
    private List<String> shouldList = new ArrayList<>();
    private List<String> excludeList = new ArrayList<>();
}