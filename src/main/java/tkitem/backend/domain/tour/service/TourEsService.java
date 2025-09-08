package tkitem.backend.domain.tour.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.KnnSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tkitem.backend.domain.scheduleType.service.EmbeddingService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class TourEsService {

    private static final String INDEX_TDS = "tour_detail_schedule_v1";
    private final ElasticsearchClient esClient;
    private final EmbeddingService embeddingService;

    /**
     * 사용자 TEXT를 임베딩하여 TDS 인덱스에 kNN 조회 후, 투어별 상위 m개 점수 평균을 반환
     * @param queryText 사용자 텍스트
     * @param allowTourIds 허용 투어 ID 집합
     * @param k 후보군 수
     * @param candidates
     * @param mTop 투어별 상위 m 개 평균
     * @return 투어 ID -> sEs 점수
     * @throws Exception
     */
    public Map<Long, Double> computeEsScores(
            String queryText,
            Set<Long> allowTourIds,
            int k, int candidates, int mTop) throws Exception{

        // 쿼리 임베딩
        float[] qv = embeddingService.embed(queryText); // 텍스트를 1536D 벡터로 변환
        List<Float> qvList = new ArrayList<>(qv.length);
        for(float v : qv) qvList.add(v);

        // KNN 검색(코사인 유사도 검색)
        KnnSearchResponse<Map> resp = esClient.knnSearch(r -> r
                .index(INDEX_TDS)
                .knn(kn -> kn.field("embedding")
                        .queryVector(qvList)
                        .k(k) // 상위 k 개 후보
                        .numCandidates(candidates)) // 내부 탐색폭을 candidates 로 지정
                .source(src -> src.filter(f -> f.includes("tour_id", "title", "schedule_date"))), // 응답 페이로드를 필요한 필드만 포함시킴
                Map.class);

        // 투어별 점수 수집
        Map<Long, List<Double>> byTour = new ConcurrentHashMap<>();
        // 각 히트별 처리
        resp.hits().hits().forEach(hit -> {
            Map<String, Object> src = hit.source();
            if(src == null) return;
            Object tidObj = src.get("tour_id");
            if(tidObj == null) return;
            Long tourId  = Long.valueOf(String.valueOf(tidObj));
            if(allowTourIds != null && !allowTourIds.contains(tourId)) return; // DB 후보군 뽑은것과 교집합 일 경우만 다음step
            byTour.computeIfAbsent(tourId, k2 -> new ArrayList<>()).add(hit.score()); // 해당 투어의 유사도 점수 추가
        });

        //투어별 상위 m개 평균
        Map<Long, Double> sEs = new ConcurrentHashMap<>();
        for (Map.Entry<Long, List<Double>> e : byTour.entrySet()) {
            List<Double> top = e.getValue().stream()
                    .sorted(Comparator.reverseOrder())
                    .limit(Math.max(mTop, 1))
                    .toList();
            double avg = top.stream().mapToDouble(Double::doubleValue).average().orElse(0.0); // ES kNN 의 코사인 유사도 점수 중 상위 mTop개 평균
            sEs.put(e.getKey(), avg);
        }

        return sEs;
    }
}
