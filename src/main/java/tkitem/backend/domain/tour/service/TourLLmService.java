package tkitem.backend.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tkitem.backend.domain.tour.dto.KeywordRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@Slf4j
public class TourLLmService {

    @Qualifier("geminiChatClient")
    private final ChatClient chatClient;

    private final ObjectMapper objectMapper;

    private static final String GEMINI_MODEL = "gemini-2.0-flash";
    private static final int MAX_PHRASES = 12;

    public TourLLmService(@Qualifier("geminiChatClient")ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 사용자 자연어(queryText)에서 should/exclude(=must not) 문구를 LLM 으로 추출해 KeywordRule 생성
     * @param queryText
     * @return
     */
    public KeywordRule buildRuleFromQueryText(String queryText){
        log.info("[buildRuleFromQueryText] queryText = {}", queryText);
        KeywordRule rule = new KeywordRule();
        rule.setKeyword(safeTrim(queryText));

        if (queryText == null || queryText.isBlank()) {
            rule.setShouldList(Collections.emptyList());
            rule.setExcludeList(Collections.emptyList());
            log.info("TOUR LLM 분류 실패 : 빈 쿼리 텍스트");
            return rule;
        }

        try {
            // LLM 보내고 값 받아옴
            String json = callGeminiForShouldExclude(queryText);

            // 노이즈 섞여도 첫 번째 JSON 객체만 추출
            String jsonOnly = extractFirstJsonObject(json);
            if (jsonOnly == null) {
                log.warn("[TourLLmService] JSON block not found. raw={}", crop(json, 300));
                rule.setShouldList(Collections.emptyList());
                rule.setExcludeList(Collections.emptyList());
                return rule;
            }

            JsonNode node = objectMapper.readTree(jsonOnly);
            List<String> should = toStringList(node.path("should"));
            List<String> exclude = toStringList(node.path("exclude"));

            should = normalizePhrases(should);
            exclude = normalizePhrases(exclude);

            if (should.size() > MAX_PHRASES) should = should.subList(0, MAX_PHRASES);
            if (exclude.size() > MAX_PHRASES) exclude = exclude.subList(0, MAX_PHRASES);

            rule.setShouldList(should);
            rule.setExcludeList(exclude);
            log.info("[buildRuleFromQueryText] TOUR LLM 분류성공 : should = {}, exclude = {}", should.size(), exclude.size());
            for(String s : should) log.info("should : {}", s);
            for(String s : exclude) log.info("exclude : {}", s);
            return rule;

        } catch (Exception e) {
            log.warn("[TourLLmService] LLM parsing failed: {}", e.toString());
            rule.setShouldList(Collections.emptyList());
            rule.setExcludeList(Collections.emptyList());
            return rule;
        }
    }

    protected String callGeminiForShouldExclude(String queryText) {
        String system = """
                너는 여행 검색 문장을 분석해서, '포함해야 할 정확 구문(should)'과 '제외해야 할 정확 구문(exclude)'만 JSON으로 출력하는 도우미야.
                - 반드시 'JSON만' 출력해. 다른 설명, 접두/접미 텍스트, 코드블록 표시는 절대 넣지 마.
                - 구문은 사용자가 입력한 한국어 '원문' 그대로 써. 형태/어순 변경 금지.
                - should: 검색 결과에 '포함되면 좋은' 구문 리스트 (정확 구문 일치용). (예시 : [골프장 가고싶어] => 골프장, 골프, cc, 18홀) 처럼 비슷한 의미를 띄는 단어를 추가
                - exclude: 검색 결과에서 '제외해야 하는' 구문 리스트 (정확 구문 일치용). (예시 : [해산물을 못먹어서 해산물 식사 없는데로 가고싶어] => 해산물, 생선, 랍스터, 조개, 회, 문어, 오징어) 처럼 비슷한, 더 구체적은 범위의 단어도 추가
                - 출력 스키마(반드시 준수):
                  {"should":[...], "exclude":[...]}
                """;

        String user = """
                입력 문장:
                "%s"
                위 입력을 기준으로 JSON만 출력하세요.
                """.formatted(safeTrim(queryText));

        String content = chatClient.prompt()
                .system(system)
                .user(user)
                .call()
                .content(); // 편의 메서드: 바로 String

        log.debug("[TourLLmService] Gemini raw content: {}", crop(content, 300));
        return content == null ? "" : content;
    }

    // -------------------- helpers --------------------

    private static String safeTrim(String s) {
        return (s == null) ? "" : s.trim();
    }

    private static String crop(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * LLM이 실수로 텍스트를 붙여도 첫 번째 JSON 객체만 뽑아낸다.
     * - ```json ... ``` 같은 마크다운이 섞여도 { ... } 구간만 추출.
     */
    private static String extractFirstJsonObject(String content) {
        if (content == null) return null;
        int start = content.indexOf('{');
        if (start < 0) return null;

        // 간단한 중괄호 매칭 (중첩 고려)
        int depth = 0;
        for (int i = start; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private List<String> toStringList(JsonNode maybeArray) {
        if (maybeArray == null || !maybeArray.isArray()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        maybeArray.forEach(n -> { if (n.isTextual()) out.add(n.asText()); });
        return out;
    }

    private List<String> normalizePhrases(List<String> in) {
        if (in == null || in.isEmpty()) return Collections.emptyList();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : in) {
            String t = safeTrim(s);
            if (!t.isEmpty()) set.add(t);
        }
        return new ArrayList<>(set);
    }
}
