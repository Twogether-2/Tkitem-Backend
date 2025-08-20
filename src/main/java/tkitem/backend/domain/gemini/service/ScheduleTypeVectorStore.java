//package tkitem.backend.domain.gemini.service;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//
//import jakarta.annotation.PostConstruct;
//import tkitem.backend.domain.gemini.mapper.GeminiMapper;
//
//import java.util.List;
//import java.util.Map;
//
//@Service
//@RequiredArgsConstructor
//public class ScheduleTypeVectorStore {
//    private final GeminiMapper geminiMapper;
//    private final GeminiEmbeddingService gemini;
//
//    private List<Label> labels;
//
//    @PostConstruct
//    public void init() {
//        var rows = geminiMapper.findAllForEmbedding(); // ID, NAME, NAME_KR, ALLTEXT
//        this.labels = rows.stream().map(r -> {
//            int id        = ((Number) r.get("ID")).intValue();
//            String name   = (String) r.get("NAME");
//            String nameKr = (String) r.get("NAME_KR");
//            String text   = normalize((String) r.get("ALLTEXT"));
//            float[] vec   = gemini.embed(text);
//            return new Label(id, name, nameKr, text, vec);
//        }).toList();
//    }
//
//    public List<Label> labels() { return labels; }
//
//    private String normalize(String s) {
//        if (s == null) return "";
//        return s.toLowerCase()
//                .replaceAll("\\([^)]*\\)", " ") // 괄호 안 데이터 전부 제거
//                .replaceAll("\\s+", " ") // 공백 압축
//                .trim();
//    }
//
//    public record Label(int id, String name, String nameKr, String text, float[] vec) {}
//}