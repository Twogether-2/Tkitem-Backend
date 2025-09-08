package tkitem.backend.domain.product_recommendation.util;

import java.util.*;
import java.util.regex.*;

public final class TagExtractor {
    private TagExtractor(){}

    private static final Map<String,String> FLAG_TO_STD = Map.of(
            "RAIN","WEATHER:RAIN",
            "HUMID","WEATHER:HUMID",
            "WIND","WEATHER:WIND",
            "SUN","WEATHER:SUN"
    );

    private static final List<Map.Entry<Pattern,String>> NOTE_RULES = List.of(
            Map.entry(Pattern.compile("자외선|\\buv\\b|upf|선크림|선글라스", Pattern.CASE_INSENSITIVE), "WEATHER:SUN"),
            Map.entry(Pattern.compile("방수|레인코트|우비|드라이백|방수팩|ipx[5678]", Pattern.CASE_INSENSITIVE), "WEATHER:RAIN"),
            Map.entry(Pattern.compile("흡습속건|통풍|메쉬|린넨|냉감|쿨", Pattern.CASE_INSENSITIVE), "WEATHER:HUMID"),
            Map.entry(Pattern.compile("방풍|윈드", Pattern.CASE_INSENSITIVE), "WEATHER:WIND"),
            Map.entry(Pattern.compile("스노클|비치|해변|래쉬가드", Pattern.CASE_INSENSITIVE), "ACTIVITY:BEACH")
    );

    public static Set<String> extractTagCodes(String notes, String itemName) {
        Set<String> out = new LinkedHashSet<>();
        String src = ((notes==null?"":notes) + " " + (itemName==null?"":itemName)).trim();

        // FLAGS:RAIN,HUMID,...
        Matcher m = Pattern.compile("FLAGS:([A-Z,]+)", Pattern.CASE_INSENSITIVE).matcher(src.toUpperCase());
        if (m.find()) {
            for (String f : m.group(1).split(",")) {
                String std = FLAG_TO_STD.get(f.trim());
                if (std != null) out.add(std);
            }
        }
        // 키워드 룰
        for (var rule : NOTE_RULES) {
            if (rule.getKey().matcher(src).find()) out.add(rule.getValue());
        }
        return out;
    }
}
