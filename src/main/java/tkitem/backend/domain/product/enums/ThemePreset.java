package tkitem.backend.domain.product.enums;

import lombok.Getter;

import java.util.List;

@Getter
public enum ThemePreset {
    FLIGHT("flight", "비행템",
            List.of("목베개","안대","귀마개","슬리퍼","여권케이스/여권지갑","파우치","보조배터리","충전 케이블","마스크(KF94, N95)")
    ),
    HOTEL("hotel", "호텔/숙소템",
            List.of("슬리퍼","안대","귀마개","여행용 세면도구","소독 티슈/스프레이","휴대용 세탁/세제","휴대용 샤워필터기","여행용 자물쇠")
    ),
    SIGHTSEEING("sightseeing", "관광템",
            List.of("물병/텀블러","모자","선크림","선글라스/고글","숄더백/미니백","보조배터리","카메라/렌즈","삼각대")
    ),
    HIKING("hiking", "등산/트레킹",
            List.of("등산화/워킹화/러닝화","트레킹 폴/등산스틱","모자","장갑","선글라스/고글","레인코트/방수 자켓","물병/텀블러","보조배터리")
    ),
    WATER("water", "물놀이템",
            List.of("스노클링 세트","물안경","수모","스마트폰 방수케이스","방수팩","비치타월","선글라스/고글","선크림")
    ),
    NATURE("nature", "자연템",
            List.of("모자","선크림","선글라스/고글","벌레퇴치제/모기스프레이","레인코트/방수 자켓","우산","물병/텀블러")
    ),
    ACTIVITY("activity", "체험 액티비티",
            List.of("등산화/워킹화/러닝화","모자","장갑","레인코트/방수 자켓","물병/텀블러","보조배터리","휴대용 선풍기","파우치")
    ),
    SPA("spa", "스파/마사지템",
            List.of("슬리퍼","보습크림/로션","립밤","클렌징 티슈")
    );

    private final String code;        // URL 슬러그
    private final String kor;         // 한글명
    private final List<String> names; // 서브카테고리 이름들만 보유

    ThemePreset(String code, String kor, List<String> names) {
        this.code = code;
        this.kor = kor;
        this.names = names;
    }

    /** "flight" / "FLIGHT" / "비행템" 등으로 매칭 */
    public static ThemePreset from(String any) {
        if (any == null || any.isBlank()) return null;
        String s = any.trim();
        for (ThemePreset p : values()) if (p.code.equalsIgnoreCase(s)) return p;
        for (ThemePreset p : values()) if (p.name().equalsIgnoreCase(s)) return p;
        for (ThemePreset p : values()) if (p.kor.equals(s)) return p;
        return null;
    }
}