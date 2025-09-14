package tkitem.backend.domain.product_recommendation.util;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public final class RecommendReasonFormatter {

    private RecommendReasonFormatter() {}

    /** 입력: ' | '로 구분된 RECOMMEND_TOKENS. 출력: 사람이 읽을 수 있는 ' | ' 구분 설명. */
    public static String toReason(String recommendTokens) {
        if (recommendTokens == null || recommendTokens.trim().isEmpty()) return "";
        String[] parts = recommendTokens.split("\\|");
        List<String> out = new ArrayList<>();

        for (String raw : parts) {
            String tok = raw == null ? "" : raw.trim();
            if (tok.isEmpty()) continue;

            if (tok.startsWith("CITY_POP:")) {
                String p = extractPercent(tok.substring("CITY_POP:".length()));
                if (!p.isEmpty()) out.add("여행 도시 인기 상위 " + p + "%");
                continue;
            }

            // MATCH:RAIN,HUMID,...
            if (tok.startsWith("MATCH:")) {
                String body = tok.substring("MATCH:".length()).trim();
                if (!body.isEmpty()) {
                    List<String> mapped = Arrays.stream(body.split("\\s*,\\s*"))
                            .filter(s -> !s.isBlank())
                            .map(RecommendReasonFormatter::mapMatchCodeKo)
                            .distinct()
                            .collect(Collectors.toList());
                    if (!mapped.isEmpty()) out.add("컨텍스트 매칭: " + String.join(", ", mapped));
                }
                continue;
            }

            // TONE:LIGHT|DARK
            if (tok.startsWith("TONE:")) {
                String v = tok.substring("TONE:".length()).trim().toUpperCase(Locale.ROOT);
                if (v.equals("LIGHT")) out.add("밝은 톤 선호 일치");
                else if (v.equals("DARK")) out.add("어두운 톤 선호 일치");
                continue;
            }

            // SAT:VIVID|MUTED
            if (tok.startsWith("SAT:")) {
                String v = tok.substring("SAT:".length()).trim().toUpperCase(Locale.ROOT);
                if (v.equals("VIVID")) out.add("비비드/고채도 선호 일치");
                else if (v.equals("MUTED")) out.add("뮤트/저채도 선호 일치");
                continue;
            }

            // STYLE:MODERN,CASUAL...
            if (tok.startsWith("STYLE:")) {
                String body = tok.substring("STYLE:".length()).trim();
                if (!body.isEmpty()) {
                    List<String> looks = Arrays.stream(body.split("\\s*,\\s*"))
                            .filter(s -> !s.isBlank())
                            .map(RecommendReasonFormatter::prettifyLookCode) // MODERN → Modern
                            .collect(Collectors.toList());
                    if (!looks.isEmpty()) out.add("선호 스타일 일치: " + String.join(", ", looks));
                }
                continue;
            }

            // FIT:SLIM|RELAXED
            if (tok.startsWith("FIT:")) {
                String v = tok.substring("FIT:".length()).trim().toUpperCase(Locale.ROOT);
                if (v.equals("SLIM")) out.add("슬림 핏 선호 일치");
                else if (v.equals("RELAXED")) out.add("릴랙스드 핏 선호 일치");
                continue;
            }

            // >>> 추가: BOLD:BOLD | BOLD:MILD <<<
            if (tok.startsWith("BOLD:")) {
                String v = tok.substring("BOLD:".length()).trim().toUpperCase(Locale.ROOT);
                if ("BOLD".equals(v)) out.add("화려한 포인트 선호 일치");
                else if ("MILD".equals(v)) out.add("무난한 스타일 선호 일치");
                continue;
            }

            // BRAND:LOYAL
            if (tok.equals("BRAND:LOYAL")) {
                out.add("브랜드 충성도(자주 구매)");
                continue;
            }

            // RATING:4.3
            if (tok.startsWith("RATING:")) {
                String v = tok.substring("RATING:".length()).trim();
                if (!v.isEmpty()) out.add("평점 " + v + "/5");
                continue;
            }

            // 알 수 없는 토큰은 무시 (필요시 out.add("기타: " + tok);)
        }

        return String.join(" | ", out);
    }

    /* ===== Helpers ===== */

    private static String extractPercent(String s) {
        if (s == null) return "";
        Matcher m = Pattern.compile("(\\d{1,3})").matcher(s);
        return m.find() ? m.group(1) : "";
    }

    private static String mapMatchCodeKo(String code) {
        if (code == null) return "";
        switch (code.trim().toUpperCase(Locale.ROOT)) {
            case "RAIN":    return "우천";
            case "HUMID":   return "습도";
            case "WIND":    return "강풍";
            case "SUN":     return "강한 햇빛";
            case "COLD":    return "추위";
            case "HOT":     return "더위";
            case "SHOPPING":return "쇼핑";
            case "TOURING": return "도시 투어";
            case "SPORT":   return "스포츠";
            case "BEACH":   return "해변";
            case "HIKING":  return "하이킹";
            default:        return code; // 미정의 코드는 원문 노출
        }
    }

    private static String prettifyLookCode(String s) {
        if (s == null) return "";
        String t = s.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return "";
        // 첫 글자 대문자 (단어별)
        StringBuilder sb = new StringBuilder(t.length());
        boolean up = true;
        for (char c : t.toCharArray()) {
            if (up && Character.isLetter(c)) { sb.append(Character.toUpperCase(c)); up = false; }
            else { sb.append(c); }
            if (c == ' ') up = true;
        }
        return sb.toString();
    }

    private static String mapPriceBucketKo(String bucket) {
        if (bucket == null || bucket.isBlank()) return "";
        bucket = bucket.trim();
        if (bucket.endsWith("+")) {
            String num = bucket.substring(0, bucket.length() - 1); // e.g., "100k"
            long won = parseKToWon(num);
            return formatKrw(won) + " 이상";
        }
        if (bucket.startsWith("≤") || bucket.startsWith("<=")) {
            String num = bucket.substring(bucket.startsWith("≤") ? 1 : 2); // e.g., "50k"
            long won = parseKToWon(num);
            return formatKrw(won) + " 이하";
        }
        return bucket;
    }

    private static long parseKToWon(String s) {
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.endsWith("k")) s = s.substring(0, s.length() - 1);
        long k = 0;
        try { k = Long.parseLong(s); } catch (NumberFormatException ignored) {}
        return k * 1000L;
    }

    private static String formatKrw(long won) {
        // 10,000원 단위로 "N만원" 표기
        if (won % 10000 == 0) return (won / 10000) + "만원";
        double man = won / 10000.0;
        return (Math.round(man * 10.0) / 10.0) + "만원";
    }
}
