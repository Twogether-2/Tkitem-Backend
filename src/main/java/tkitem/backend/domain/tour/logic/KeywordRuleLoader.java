package tkitem.backend.domain.tour.logic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import tkitem.backend.domain.tour.dto.KeywordRule;
import tkitem.backend.global.error.ErrorCode;
import tkitem.backend.global.error.exception.BusinessException;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class KeywordRuleLoader {

    private final Map<String, KeywordRule> byKeyword = new ConcurrentHashMap<>();
    private final Resource keywordFile;

    public KeywordRuleLoader(@Value("classpath:dummy/keyword.json") Resource keywordFile) {
        this.keywordFile = keywordFile;
    }

    @PostConstruct
    public void load() {
        ObjectMapper om = new ObjectMapper();
        try (InputStream is = keywordFile.getInputStream()) {
            JsonNode root = om.readTree(is);

            if (!root.isArray()) throw new BusinessException("keyword.json must be an array", ErrorCode.CONFIG_INVALID);
            for (JsonNode n : root) {
                KeywordRule r = new KeywordRule();
                r.setKeyword(optText(n,"keyword"));
                r.setCountry(optText(n,"country"));
                r.setCountryGroup(optText(n,"countryGroup"));
                r.setShouldList(optStringList(n, "shouldList"));
                r.setExcludeList(optStringList(n, "excludeList"));

                if (r.getKeyword() != null && !r.getKeyword().isBlank()) {
                    byKeyword.put(r.getKeyword(), r);
                }
            }
            log.info("[KeywordRuleLoader] loaded {} rules", byKeyword.size());
        } catch (Exception e) {
            throw new BusinessException("Failed to load keyword.json: " + e.getMessage(), ErrorCode.CONFIG_LOAD_FAILED);
        }
    }

    public KeywordRule getRequired(String keyword) {
        KeywordRule r = byKeyword.get(keyword);
        if(r == null){
            throw new BusinessException("Keyword not found: " + keyword, ErrorCode.CONFIG_INVALID);
        }
        return r;
    }

    private static String optText(JsonNode n, String field) {
        return n.has(field) && !n.get(field).isNull() ? n.get(field).asText() : null;
    }
    private static List<String> optStringList(JsonNode n, String... candidates) {
        for (String f : candidates) {
            if (n.has(f) && n.get(f).isArray()) {
                List<String> out = new ArrayList<>();
                n.get(f).forEach(x -> out.add(x.asText()));
                return out;
            }
        }
        return new ArrayList<>();
    }
}