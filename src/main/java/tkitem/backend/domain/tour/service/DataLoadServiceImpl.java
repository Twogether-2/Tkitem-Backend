package tkitem.backend.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.tour.mapper.TourMapper;
import tkitem.backend.domain.tour.vo.Tour;
import tkitem.backend.domain.tour.vo.TourDetailSchedule;
import tkitem.backend.domain.tour.vo.TourPackage;

import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataLoadServiceImpl implements DataLoadService {

    private final TourMapper tourMapper;
    private final ObjectMapper objectMapper; // JSON 파싱을 위한 ObjectMapper

    @Override
    @Transactional
    public void loadDataFromCsv(String filePath) throws Exception {
        log.info("CSV 데이터 적재를 시작합니다. 파일 경로: {}", filePath);

        // tripCode를 기준으로 데이터를 그룹화하기 위한 Map
        Map<String, List<String[]>> groupedByTripCode = new HashMap<>();

        // 1. CSV 파일을 읽어 tripCode 기준으로 메모리에 그룹화
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] header = reader.readNext(); // 헤더 스킵
            if (header == null) {
                log.warn("CSV 파일이 비어있거나 헤더가 없습니다.");
                return;
            }

            String[] line;
            while ((line = reader.readNext()) != null) {
                // tripCode는 첫 번째 컬럼(인덱스 0)이라고 가정
                String tripCode = line[0];
                groupedByTripCode.computeIfAbsent(tripCode, k -> new ArrayList<>()).add(line);
            }
        } catch (IOException | CsvValidationException e) {
            log.error("CSV 파일을 읽는 중 오류가 발생했습니다.", e);
            throw e;
        }

        log.info("총 {}개의 고유한 tripCode를 발견했습니다. 데이터 처리를 시작합니다.", groupedByTripCode.size());

        // 2. 그룹화된 데이터를 하나씩 처리
        for (Map.Entry<String, List<String[]>> entry : groupedByTripCode.entrySet()) {
            String tripCode = entry.getKey();
            List<String[]> rows = entry.getValue();

            try {
                // 3. 중복 확인
                if (tourMapper.findTourByTripCode(tripCode) != null) {
                    log.warn("이미 존재하는 tripCode입니다. 건너뜁니다: {}", tripCode);
                    continue;
                }

                // 첫 번째 행의 detail_json을 대표로 사용
                String detailJsonStr = rows.get(0)[7]; // detail_json은 8번째 컬럼(인덱스 7)
                JsonNode rootNode = objectMapper.readTree(detailJsonStr);

                // 4. Tour 객체 생성 및 삽입
                Tour tour = createTourFrom(tripCode, rootNode, detailJsonStr);
                tourMapper.insertTour(tour);
                Long generatedTourId = tour.getTourId(); // 생성된 tour_id 가져오기

                // 5. TourPackage 리스트 생성 및 삽입
                List<TourPackage> tourPackages = createTourPackagesFrom(rows, generatedTourId);
                if (!tourPackages.isEmpty()) {
                    tourMapper.insertTourPackageList(tourPackages);
                }

                // 6. TourDetailSchedule 리스트 생성 및 삽입
                List<TourDetailSchedule> schedules = createSchedulesFrom(rootNode, generatedTourId);
                if (!schedules.isEmpty()) {
                    tourMapper.insertTourDetailScheduleList(schedules);
                }

                log.info("tripCode '{}' 처리 완료 (Tour 1개, Package {}개, Schedule {}개)",
                        tripCode, tourPackages.size(), schedules.size());

            } catch (Exception e) {
                log.error("tripCode '{}' 처리 중 오류가 발생하여 건너뜁니다. 오류: {}", tripCode, e.getMessage());
                // @Transactional에 의해 이 tripCode에 대한 모든 DB 작업은 롤백됨
            }
        }
        log.info("CSV 데이터 적재를 완료했습니다.");
    }

    private Tour createTourFrom(String tripCode, JsonNode rootNode, String detailJsonStr) {
        JsonNode itinerary = rootNode.path("itinerary");
        return Tour.builder()
                .tripCode(tripCode)
                .title(rootNode.path("title").asText())
                .provider(rootNode.path("tourOperator").path("name").asText())
                .durationDays(itinerary.path("days").asInt())
                .nights(itinerary.path("nights").asInt())
                .itineraryJson(detailJsonStr)
                //.imgUrl(rootNode.path("photos").get(0).path("url").asText()) // 사진이 없을 수 있으므로 주의
                .build();
    }

    private List<TourPackage> createTourPackagesFrom(List<String[]> rows, Long tourId) {
        List<TourPackage> packages = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (String[] row : rows) {
            packages.add(TourPackage.builder()
                    .tourId(tourId)
                    .packageDateCode(row[1]) // travelId
                    .departureDate(LocalDateTime.parse(row[2], formatter).toLocalDate())
                    .returnDate(LocalDateTime.parse(row[3], formatter).toLocalDate())
                    .price(Integer.parseInt(row[4]))
                    .build());
        }
        return packages;
    }

    private List<TourDetailSchedule> createSchedulesFrom(JsonNode rootNode, Long tourId) {
        List<TourDetailSchedule> schedules = new ArrayList<>();
        JsonNode dailies = rootNode.path("itinerary").path("dailies");

        if (dailies.isArray()) {
            for (JsonNode day : dailies) {
                int dayNum = day.path("dayNum").asInt();
                JsonNode items = day.path("items");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        JsonNode contents = item.path("contents");
                        schedules.add(TourDetailSchedule.builder()
                                .tourId(tourId)
                                .title(contents.path("name").asText())
                                .description(contents.path("description").asText())
                                .sortOrder(item.path("sort").asInt())
                                // scheduleDate 계산 로직은 정책에 따라 추가 필요
                                .build());
                    }
                }
            }
        }
        return schedules;
    }
}