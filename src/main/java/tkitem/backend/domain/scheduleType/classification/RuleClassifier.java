package tkitem.backend.domain.scheduleType.classification;

import java.util.*;
import java.util.regex.Pattern;

public class RuleClassifier {

    public static final double MIN_SCORE = 0.65;
    public static final double MIN_MARGIN = 0.10;

    private final Map<String, List<Pattern>> dict = Map.ofEntries(
            Map.entry("FLIGHT", keyword("항공","비행기","탑승","항공사","공항")),
            Map.entry("TRANSFER", keyword("이동", "버스", "차량", "기차", "오토바이", "보트", "유람선", "툭툭")),
            Map.entry("GUIDE", keyword("가이드")),
            Map.entry("HOTEL", keyword("호텔", "리조트", "체크인", "체크아웃")),
            Map.entry("HOTEL_STAY", keyword("투숙", "숙박")),
            Map.entry("SIGHTSEEING", keyword("관광", "시내관광", "전통", "거리")),
            Map.entry("LANDMARK", keyword("랜드마크", "타워", "성당", "유네스코")),
            Map.entry("MUSEUM_HERITAGE", keyword("박물관", "유적", "사원", "성당", "미술관", "극장")),
            Map.entry("PARK_NATURE", keyword("공원", "자연", "정원", "산책", "숲", "마운틴", "초원", "산림")),
            Map.entry("ACTIVITY", keyword("체험", "짚라인", "번지", "다이빙", "스노클", "스노클링", "수영", "스키", "보드")),
            Map.entry("HIKING_TREKKING", keyword("트레킹", "트래킹", "등산", "등반", "마운틴")),
            Map.entry("SHOW", keyword("쇼", "공연", "극장", "오페라", "뮤지컬", "연극")),
            Map.entry("SPA_MASSAGE", keyword("스파", "마사지", "안마")),
            Map.entry("SHOPPING", keyword("쇼핑", "면세점", "마트", "백화점", "시장")),
            Map.entry("MEAL", keyword("식사", "조식", "중식", "석식", "레스토랑", "뷔페", "런치", "디너")),
            Map.entry("CAFE", keyword("카페", "디저트", "커피")),
            Map.entry("FREE_TIME", keyword("자유")),
            Map.entry("ETC", keyword("기타")),
            Map.entry("SWIM_SNORKELING", keyword("수영", "다이빙", "스노클"))

    );

    private static List<Pattern> keyword(String... words){
        List<Pattern> ps = new ArrayList<>();
        for(String w : words) ps.add(Pattern.compile(w, Pattern.CASE_INSENSITIVE));
        return ps;
    }

    // 1차 룰 스코어링
    public Map<String, Double> score(String title, String desc, String sourceCategory){
        String text = ((title==null?"":title)+" "+(desc==null?"":desc)+" "+(sourceCategory==null?"":sourceCategory)).toLowerCase();
        Map<String, Double> scores = new HashMap<>();
        dict.forEach((type, patterns) -> {
            boolean hit = patterns.stream().anyMatch(p -> p.matcher(text).find());
            scores.put(type, hit ? 1.0 : 0.0);
        });

        // 룰 우선순쉬 또는 보정이 필요하면 가중치 조절 필요
        return scores;
    }

    public static class Top2 {
        public String top1Type;
        public double top1Score;
        public String top2Type;
        public double top2Score;
        public Top2(String t1,double s1,String t2,double s2){
            top1Type=t1;
            top1Score=s1;
            top2Type=t2;
            top2Score=s2;
        }
    }

    // 상위2 추출
    public Top2 top2(Map<String,Double> scores){
        return scores.entrySet().stream()
                .sorted((a,b)->Double.compare(b.getValue(), a.getValue()))
                .limit(2)
                .collect(() -> new Top2(null,0,null,0),
                        (acc,e)->{
                            if (acc.top1Type==null){
                                acc.top1Type=e.getKey();
                                acc.top1Score=e.getValue();
                            }
                            else if (acc.top2Type==null){
                                acc.top2Type=e.getKey();
                                acc.top2Score=e.getValue();
                            }
                        },
                        (a,b)->{});
    }

    public Map<String, List<Pattern>> getPatternDict() {
        return this.dict;
    }

    public static String normalize(String s){
        if(s == null || s.isEmpty()) return "";
        return s.toLowerCase().replaceAll("\\s+"," ").trim();
    }
}
