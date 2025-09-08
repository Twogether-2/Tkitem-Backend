package tkitem.backend.domain.scheduleType.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tkitem.backend.domain.scheduleType.service.ScheduleTypeExemplarSeeder;
import tkitem.backend.domain.scheduleType.service.TourTypePipelineService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/tourType")
public class TourScheduleTypeController {
    private final TourTypePipelineService pipeline;
    private final ScheduleTypeExemplarSeeder seeder;

    @PostMapping("/run/pipeline")
    public ResponseEntity<String> run(@RequestParam(defaultValue = "1000") int batchSize) throws Exception {
        pipeline.runOnce(batchSize);
        return ResponseEntity.ok("pipeline run Success");
    }

    @PostMapping("/seed/exemplars")
    public ResponseEntity<String> seedExemplars() throws Exception {
        seeder.seedExemplars();
        return ResponseEntity.ok("Exemplar seeding successful");
    }
}
