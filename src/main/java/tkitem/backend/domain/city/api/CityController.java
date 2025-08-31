package tkitem.backend.domain.city.api;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tkitem.backend.domain.city.service.CityService;

import java.io.IOException;

@RestController
@RequestMapping("/api/city")
@RequiredArgsConstructor
public class CityController {

    private final CityService cityService;
    private final ResourceLoader resourceLoader;

    @PostMapping("/init")
    public ResponseEntity<String> initializeCityData() {
        try {
            // 1. ResourceLoader를 사용하여 클래스패스에서 파일을 찾습니다.
            Resource resource = resourceLoader.getResource("classpath:dummy/도시.json");
            
            // 2. 해당 파일의 절대 경로를 얻어옵니다.
            String absolutePath = resource.getFile().getAbsolutePath();
            
            // 3. Service에 파일 경로를 전달하여 데이터를 초기화합니다.
            cityService.addCitiesFromJson(absolutePath);
            
            return ResponseEntity.ok("도시 데이터 초기화에 성공했습니다.");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("도시 데이터 초기화 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}
