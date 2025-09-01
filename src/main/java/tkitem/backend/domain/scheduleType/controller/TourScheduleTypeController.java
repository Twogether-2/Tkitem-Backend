package tkitem.backend.domain.scheduleType.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tkitem.backend.domain.scheduleType.service.TourTypePipelineService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/tourType")
public class TourScheduleTypeController {
    private final TourTypePipelineService pipeline;

    @PostMapping("/run/pipeline")
    public ResponseEntity<String> run(@RequestParam(defaultValue = "1000") int batchSize) throws Exception {
        pipeline.runOnce(batchSize);
        return ResponseEntity.ok("pipeline run Success");
    }
}
