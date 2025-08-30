package tkitem.backend.domain.preference.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Preference {
	private Long preferenceId;   // 취향 기본키
	private Long memberId;       // 회원 기본키 (외래키)
	private int brightness;  // 0=밝은옷(B) ~ 100=어두운옷(D)
	private int boldness;    // 0=무난한옷(M) ~ 100=튀는옷(T)
	private int fit;         // 0=딱붙(F) ~ 100=오버핏(O)
	private int color;       // 0=비비드(V) ~ 100=뉴트럴(N)
	private String firstLook; // 첫번째 선호 룩
	private String secondLook; // 두번째 선호 룩
}
