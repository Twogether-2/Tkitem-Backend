package tkitem.backend.domain.tour.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tkitem.backend.domain.tour.service.DataLoadService;

@RestController
@RequestMapping("/api/tour")
@RequiredArgsConstructor
public class TourController {
    private final DataLoadService dataLoadService;
    private final ResourceLoader resourceLoader;

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


}
