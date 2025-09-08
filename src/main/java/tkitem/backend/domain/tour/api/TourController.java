package tkitem.backend.domain.tour.api;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tkitem.backend.domain.member.vo.Member;
import tkitem.backend.domain.tour.dto.request.TourRecommendationRequestDto;
import tkitem.backend.domain.tour.dto.response.TourCommonRecommendDto;
import tkitem.backend.domain.tour.dto.response.TourPackageDetailDto;
import tkitem.backend.domain.tour.dto.response.TourRecommendationResponseDto;
import tkitem.backend.domain.tour.service.DataLoadService;
import tkitem.backend.domain.tour.service.TourFacadeService;
import tkitem.backend.domain.tour.service.TourService;

import java.util.List;

@RestController
@RequestMapping("/api/tour")
@RequiredArgsConstructor
@Slf4j
public class TourController {
    private final DataLoadService dataLoadService;
    private final TourFacadeService tourFacadeService;
    private final TourService tourService;

    @PostMapping("/init")
    public ResponseEntity<String> initTour(){
        try{
            // 외부 CSV 파일의 절대 경로를 지정합니다.
            String csvFilePath = "C:\\Users\\pch\\git\\TeamProject\\3st_team\\data\\trip_data.json";
            dataLoadService.loadDataFromCsv(csvFilePath);
            return ResponseEntity.ok("투어 데이터 초기화에 성공했습니다.");
        } catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("투어 데이터 초기화 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @PostMapping("/recommend")
    @Operation(
            summary = "투어 추천",
            description = "요금, 지역, 함께가는 사람(미완), 여행 스타일, 일정, 자유텍스트 기반으로 유사한 여행 상품을 추천합니다."
    )
    public ResponseEntity<List<TourRecommendationResponseDto>> recommend(
            @RequestBody TourRecommendationRequestDto req,
            @RequestParam(name = "text", required = false) String queryText,
            @RequestParam(name = "topN", defaultValue = "5") int topN,
            @AuthenticationPrincipal Member member
            ) throws Exception{
        // [추가] 요청 파라미터 요약 로그
        log.info("[REQ] text={}, topN={}", queryText, topN);
        log.info("[REQ] date {} ~ {}, price {} ~ {}, tags={}, groupId={}",
                req.getDepartureDate(), req.getReturnDate(),
                req.getPriceMin(), req.getPriceMax(),
                req.getTagIdList(), req.getGroupId());

        // locations 상세 로그 (null-safe)
        if (req.getLocations() == null) {
            log.info("[REQ] locations = null");
        } else if (req.getLocations().isEmpty()) {
            log.info("[REQ] locations = [] (empty)");
        } else {
            for (int i = 0; i < req.getLocations().size(); i++) {
                var loc = req.getLocations().get(i);
                log.info("[REQ] locations[{}] group='{}', country='{}', city='{}'",
                        i,
                        loc.getCountryGroup(),
                        loc.getCountry(),
                        loc.getCity());
            }
        }
        List<TourRecommendationResponseDto> responseDtodList = tourFacadeService.recommend(req, queryText, topN, member);
        log.info("[RES] size={}", responseDtodList == null ? null : responseDtodList.size());
        return ResponseEntity.ok(responseDtodList);
    }

    @GetMapping("/{tourPackageId:\\d+}")
    @Operation(
            summary = "투어 패키지 조회",
            description = "투어 패키지 PK 값으로 투어 패키지 세부정보 조회가 가능합니다."
    )
    public ResponseEntity<TourPackageDetailDto> getTourPackage(
            @AuthenticationPrincipal Member member,
            @PathVariable Long tourPackageId){

        TourPackageDetailDto responseDto = tourService.getTourPackageDetail(tourPackageId);

        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/recentRecommend")
    @Operation(
            summary = "최근에 추천받았던 패키지 목록조회",
            description = "recommend 요청으로 받았으나 내 여행에 담지 않았던 여행 조회"
    )
    public ResponseEntity<List<TourCommonRecommendDto>> recentRecommend(
            @AuthenticationPrincipal Member member
    ){
        List<TourCommonRecommendDto> tourCommonRecommendDtos = tourService.getRecentRecommendedTours(member);
        for(TourCommonRecommendDto dto : tourCommonRecommendDtos){
            log.info("tourId={}, title={}, tourPackageId={}, price={}", dto.getTourId(), dto.getTitle(), dto.getTourPackageId(), dto.getPrice());
        }
        return ResponseEntity.ok(tourCommonRecommendDtos);
    }

    @GetMapping("/topRank/{topN}")
    @Operation(
            summary = "가장 저장이 많이 된 패키지 목록 조회",
            description = "오늘 날짜보다 1일 뒤의 패키지들부터 저장 많이된 순 + 내가 담지 않은 패키지 로 조회"
    )
    public ResponseEntity<List<TourCommonRecommendDto>> topRank(
            @AuthenticationPrincipal Member member,
            @PathVariable Integer topN
    ) {
        List<TourCommonRecommendDto> tourCommonRecommendDtos = tourService.getTopRankedTours(member, topN);
        log.info("topN={}", topN);
        return ResponseEntity.ok(tourCommonRecommendDtos);
    }
}
