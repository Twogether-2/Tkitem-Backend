package tkitem.backend.domain.scheduleType.classification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.*;

/**
 * 규칙(키워드/가중치) 기반의 1차 분류
 */
// TODO :
//  1. 기내식 없음 같은 경우가 MEAL 점수가 그대로 들어감
//  2. default_type 에 따른 분류가 제대로 되지 않음
@Component
public class RuleClassifier {

    // 가중치 키워드 구조체
    private static class KW {
        final Pattern p;
        final List<Pattern> and; // and 그룹용(null 가능)
        final double wTitle;
        final double wDesc;
        KW(String word, double wTitle, double wDesc) {
            this.p = Pattern.compile(word, Pattern.CASE_INSENSITIVE);
            this.and = null;
            this.wTitle = wTitle; this.wDesc = wDesc;
        }
        KW(List<String> andWords, double wTitle, double wDesc) {
            this.p = null;
            this.and = andWords.stream()
                    .map(w -> Pattern.compile(w, Pattern.CASE_INSENSITIVE))
                    .toList();
            this.wTitle = wTitle; this.wDesc = wDesc;
        }
        boolean isAnd() { return and != null && !and.isEmpty(); }
    }

    private final Map<String, List<KW>> dict;

    public RuleClassifier(@Value("${rule.classifier.path}")Resource json, ObjectMapper om){
        try {
            // JSON: [{label,word,wTitle,wDesc}, ...]
            var rows = om.readValue(json.getInputStream(),
                    new TypeReference<List<Map<String, Object>>>() {});
            this.dict = rows.stream()
                    .filter(m -> m.get("label") != null && (m.get("word") != null || m.get("and") != null))
                    .collect(groupingBy(
                            m -> String.valueOf(m.get("label")).toUpperCase(),
                            mapping(m -> {
                                double wt = ((Number)m.get("wTitle")).doubleValue();
                                double wd = ((Number)m.get("wDesc")).doubleValue();
                                Object and = m.get("and");
                                if (and instanceof List<?> list) {
                                    @SuppressWarnings("unchecked")
                                    List<String> words = (List<String>) list;
                                    return new KW(words, wt, wd); // AND 그룹
                                }
                                return new KW(String.valueOf(m.get("word")), wt, wd); // 단일
                            }, toList())
                    ));
        } catch (Exception e) {
            throw new RuntimeException("Rule keywords 로드 실패", e);
        }
        if (this.dict.isEmpty()) {
            throw new IllegalStateException("Rule keywords 비어 있음: " + json);
        }
    }

    // 1차 룰 스코어링
    public Map<String, Double> score(String title, String description, String defaultType){
        String t = normalize(title);
        String d = normalize(description);

        Map<String, Double> base = new HashMap<>();

        if (defaultType != null) {
            // MEAL 타입 특별 처리
            if (defaultType.equalsIgnoreCase("MEAL")) {
                // 부정 키워드("없음", "불포함") 확인
                if (t.contains("없음") || d.contains("없음") || t.contains("불포함") || d.contains("불포함")) {
                    base.put("MEAL", 0.0);
                    return base;
                }

                // MEAL 라벨에 대해서만 점수 계산 후 즉시 반환
                List<KW> mealKeywords = dict.get("MEAL");
                if (mealKeywords != null) {
                    double best = 0.0;
                    boolean blocked = false;
                    for (var kw : mealKeywords) {
                        double sKw = 0.0;
                        if (!kw.isAnd()) { // 단일 키워드
                            boolean hitT = kw.p.matcher(t).find();
                            boolean hitD = kw.p.matcher(d).find();
                            if ((hitT && kw.wTitle == 0.0) || (hitD && kw.wDesc == 0.0)) {
                                blocked = true; best = 0.0; break;
                            }
                            if (hitT) sKw = Math.max(sKw, kw.wTitle);
                            if (hitD) sKw = Math.max(sKw, kw.wDesc);
                        } else { // AND 키워드
                            String td = (t + " " + d).trim();
                            boolean allInTD = kw.and.stream().allMatch(p -> p.matcher(td).find());
                            if (allInTD) {
                                if (kw.wTitle == 0.0 || kw.wDesc == 0.0) {
                                    blocked = true; best = 0.0; break;
                                }
                                sKw = Math.max(kw.wTitle, kw.wDesc);
                            }
                        }
                        if (sKw > best) best = sKw;
                    }
                    if (blocked) {
                        base.put("MEAL", 0.0);
                    } else if (best > 0.0) {
                        base.put("MEAL", Math.min(1.0, best));
                    }
                }
                return base; // MEAL 처리 후 종료
            }

            // ACCOMMODATION 타입 특별 처리
            if (defaultType.equalsIgnoreCase("ACCOMMODATION")) {
                // parseHotelRating 로직만 실행 후 즉시 반환
                base.put("REST", parseHotelRating(d)); // description(d) 사용
                return base; // ACCOMMODATION 처리 후 종료
            }
        }

        // --- 기본 로직 (defaultType이 MEAL이나 ACCOMMODATION이 아닌 경우) ---
        String td = (t + " " + d).trim();

        for (var e : dict.entrySet()) {
            // MEAL 라벨은 defaultType=MEAL일 때만 처리되었으므로 여기서는 항상 스킵
            if ("MEAL".equals(e.getKey())) continue;

            double best = 0.0;
            boolean blocked = false;
            for (var kw : e.getValue()) {
                double sKw = 0.0;
                if(!kw.isAnd()) { // 단일 키워드 조합이면
                    boolean hitT = kw.p.matcher(t).find();
                    boolean hitD = kw.p.matcher(d).find();
                    if ((hitT && kw.wTitle == 0.0) || (hitD && kw.wDesc == 0.0)) {
                        blocked = true; best = 0.0; break; // 가중치값 0.0 으로 매칭시 종료
                    }
                    if (hitT) sKw = Math.max(sKw, kw.wTitle);
                    if (hitD) sKw = Math.max(sKw, kw.wDesc);
                } else {
                    boolean allInTD = kw.and.stream().allMatch(p -> p.matcher(td).find());
                    if (allInTD){
                        if (kw.wTitle == 0.0 || kw.wDesc == 0.0) {
                            blocked = true; best = 0.0; break; // AND에도 0.0 규칙 적용 (둘 중 하나라도 0.0이면 차단)
                        }
                        sKw = Math.max(kw.wTitle, kw.wDesc);
                    }
                }
                if (sKw > best) best = sKw;
            }
            if(blocked){
                base.put(e.getKey(), 0.0);
            } else if (best > 0.0){
                base.put(e.getKey(), Math.min(1.0, best));
            }
        }

        return base;
    }

    // 호텔 평점 파싱 → 1.0 ~ 0.0 가중 반환. REST 타입을 위해서
    private double parseHotelRating(String text) {
        if (text == null) return 0.0;
        // 숫자에 붙은 소수 방지: 앞뒤가 숫자가 아닌 경우만 (예: 15.0 방지)
        Matcher m = Pattern
                .compile("(?<!\\d)([0-5]\\.0)(?!\\d)")
                .matcher(text);

        if(!m.find()) return 0.0;

        return switch (m.group(1)) {
            case "5.0" -> 1.00;
            case "4.0" -> 0.80;
            case "3.0" -> 0.60;
            case "2.0" -> 0.40;
            case "1.0" -> 0.20;
            default -> 0.50;
        };
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

    /**
     * top1 점수 >= 0.65, top1 - top2 >= 0.1 이면 rull 만으로도 분류 가능
     * @param scores
     * @return
     */
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

    public static String normalize(String s){
        if(s == null || s.isEmpty()) return "";
        return s.toLowerCase().replaceAll("\\s+"," ").trim();
    }
}