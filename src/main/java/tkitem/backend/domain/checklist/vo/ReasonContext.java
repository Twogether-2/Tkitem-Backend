package tkitem.backend.domain.checklist.vo;

import java.util.List;
//LLM에 전달할 컨텍스트
public record ReasonContext(
        Long tripId,

        Integer monthNum,
        Integer dayCount,
        Integer nightCount,
        List<String> cities,
        List<String> countries,
        String headlinePlace,  // 타이틀에 쓸 대표 지명

        List<String> weatherFlags,   // 예: ["RAIN","HUMID"]
        List<String> activities,     // 예: ["FLIGHT","SIGHTSEEING"]
        List<String> reasons,        // 룰 문장 조각( | 분리 )
        List<String> historyHints,   // "HIST:" 스니펫

        List<ItemEvidence> itemEvidences
) {
    public static record ItemEvidence(
            String itemName,
            Integer scheduleDate,
            String tier,
            Double score,
            List<String> flags,            // ["RAIN","HUMID"]
            List<String> reasonSnippets    // ["전역룰: ...", "국가룰(...): ..."]
    ) {}
}
