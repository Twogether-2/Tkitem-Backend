package tkitem.backend.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.city.mapper.CityMapper;
import tkitem.backend.domain.tour.mapper.TourMapper;
import tkitem.backend.domain.tour.vo.Tour;
import tkitem.backend.domain.tour.vo.TourCity;
import tkitem.backend.domain.tour.vo.TourDetailSchedule;
import tkitem.backend.domain.tour.vo.TourPackage;

import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataLoadServiceImpl implements DataLoadService {

    private final TourMapper tourMapper;
    private final CityMapper cityMapper;
    private final ObjectMapper objectMapper; // JSON 파싱을 위한 ObjectMapper

    // tripCode 유효성 검사를 위한 정규식 (영대문자, 숫자로만 이루어진 12~17자)
    private static final Pattern TRIP_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{12,17}$");

    private boolean isValidTripCode(String tripCode) {
        if (tripCode == null || tripCode.isEmpty()) {
            return false;
        }
        return TRIP_CODE_PATTERN.matcher(tripCode).matches();
    }

    // TODO :
    //  1. TOUR 의 summary 속성 작성(AI 필요)
    //  2. TOUR 의 hotelRating 을 어떻게 할지
    @Override
    public void loadDataFromCsv(String filePath) throws Exception {
        log.info("CSV 데이터 적재를 시작합니다. 파일 경로: {}", filePath);

        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] header = reader.readNext(); // 헤더 스킵
            if (header == null) {
                log.warn("CSV 파일이 비어있거나 헤더가 없습니다.");
                return;
            }

            String currentTripCode = null;
            List<String[]> currentRows = new ArrayList<>();
            String[] line;
            long lineCount = 1; // 헤더 이후부터 라인 수 계산
            int consecutiveEmptyLines = 0; // 연속된 빈 줄 카운터

            while (true) {
                line = reader.readNext();
                lineCount++;

                if (line == null || line.length == 0 || (line.length == 1 && (line[0] == null || line[0].isEmpty()))) {
                    consecutiveEmptyLines++;
                    log.warn("빈 줄 발견 ({}번째 연속). Line: {}", consecutiveEmptyLines, lineCount);
                    if (consecutiveEmptyLines >= 5) {
                        log.info("연속된 빈 줄이 5개 이상이므로 데이터 처리를 종료합니다.");
                        break;
                    }
                    continue;
                }
                // 데이터가 있는 라인을 만나면 카운터 초기화
                consecutiveEmptyLines = 0;

                String tripCode = line[0];

                // tripCode 유효성 검사
                if (!isValidTripCode(tripCode)) {
                    log.warn("유효하지 않은 tripCode 형식입니다. (Line: {}) 건너뜁니다: {}", lineCount, tripCode);
                    continue;
                }

                // 첫 번째 tripCode 처리
                if (currentTripCode == null) {
                    currentTripCode = tripCode;
                }

                // tripCode가 변경되었을 때, 이전까지 모아둔 데이터를 처리
                if (!currentTripCode.equals(tripCode)) {
                    processTripCodeGroup(currentTripCode, currentRows);
                    // 다음 그룹을 위해 초기화
                    currentTripCode = tripCode;
                    currentRows.clear();
                }

                currentRows.add(line);
            }

            // 파일의 마지막 그룹 처리
            if (!currentRows.isEmpty()) {
                processTripCodeGroup(currentTripCode, currentRows);
            }

        } catch (IOException | CsvValidationException e) {
            log.error("CSV 파일을 읽는 중 오류가 발생했습니다.", e);
            throw e;
        }

        log.info("CSV 데이터 적재를 완료했습니다.");
    }

    @Transactional
    public void processTripCodeGroup(String tripCode, List<String[]> rows) {
        try {
            // 3. 중복 확인
            // Tour 가 새로운 녀석이면
            // Tour 새로 등록
            // TourCity 새로 등록
            // TourDetailSchedule 새로 등록
            // TourPackage 새로 등록

            if (tourMapper.findTourByTripCode(tripCode) == null) {
                // 첫 번째 행의 detail_json을 대표로 사용
                String detailJsonStr = rows.get(0)[7]; // detail_json은 8번째 컬럼(인덱스 7)
                JsonNode rootNode = objectMapper.readTree(detailJsonStr);

                // 도시 이름 수집
                // 각 일정 내에서 방문 도시 목록으로도 확인 필요함.
                Map<String, Long> cityNames = new HashMap<>();
                JsonNode placesNode = rootNode.get("places");
                if(placesNode.isArray()){
                    for(JsonNode place : placesNode){
                        Long cityId = cityMapper.findCityIdByName(place.get("name").asText());
                        cityNames.put(place.get("name").asText(), cityId);
                    }
                }

                // Tour 객체 생성 및 삽입
                Tour tour = createTourFrom(tripCode, rootNode, detailJsonStr);
                tourMapper.insertTour(tour);
                Long generatedTourId = tour.getTourId(); // 생성된 tour_id 가져오기

                // TourDetailSchedule 리스트 생성 및 삽입
                List<TourDetailSchedule> schedules = createSchedulesFrom(rootNode, generatedTourId, cityNames);
                if (!schedules.isEmpty()) {
                    tourMapper.insertTourDetailScheduleList(schedules);
                }

                // TourCity 리스트 생성 및 삽입
                // places 로 조회
                // city 에 city_name 으로 검색해서 city_id 속성들 뽑아와서 저장
                List<TourCity> tourCityList = createCitiesForm(generatedTourId, cityNames);
                if (!tourCityList.isEmpty()) {
                    tourMapper.insertTourCityList(tourCityList);
                }

                // TourPackage 리스트 생성 및 삽입
                List<TourPackage> tourPackages = createTourPackagesFrom(rows, generatedTourId);
                if (!tourPackages.isEmpty()) {
                    tourMapper.insertTourPackageList(tourPackages);
                }

                log.info("tripCode '{}' 처리 완료 (Tour 1개, Package {}개, Schedule {}개)",
                        tripCode, tourPackages.size(), schedules.size());
                // 성공적으로 처리된 tripCode를 명확하게 로그로 남김
                log.info("SUCCESSFULLY PROCESSED tripCode: {}", tripCode);
            }

            // Tour 가 이미 들어가있으면
            // TourPackage 를 tourId 로 조회해서 리스트를 가져와서
            // 중복되지 않는 package_date_code(셀에선 travelId) 인 경우
            // TourPackage 만 저장하면 된다.


        } catch (Exception e) {
            log.error("tripCode '{}' 처리 중 오류가 발생하여 건너뜁니다. 오류: {}", tripCode, e.getMessage());
            // @Transactional에 의해 이 tripCode에 대한 모든 DB 작업은 롤백됨
        }
    }


    private Tour createTourFrom(String tripCode, JsonNode rootNode, String detailJsonStr) {
        JsonNode itinerary = rootNode.path("itinerary");

        // 'tags' 처리: displayName이 있는 태그만 쉼표로 구분하여 문자열로 만듭니다.
        StringBuilder tagsBuilder = new StringBuilder();
        JsonNode tagsNode = rootNode.path("tags");
        if (tagsNode.isArray()) {
            for (JsonNode tag : tagsNode) {
                String displayName = tag.path("displayName").asText(null);
                if (displayName != null && !displayName.isEmpty()) {
                    if (!tagsBuilder.isEmpty()) {
                        tagsBuilder.append(",");
                    }
                    tagsBuilder.append(displayName);
                }
            }
        }

        return Tour.builder()
                .tripCode(tripCode)
                .title(rootNode.path("title").asText())
                .provider(rootNode.path("tourOperator").path("name").asText())
                .durationDays(itinerary.path("days").asInt())
                .nights(itinerary.path("nights").asInt())
                .bookingUrl("https://tripstore.thehyundaitravel.com/products/" + tripCode + "?travelId=" + rootNode.path("id").asText())
                .sourceUrl("https://api.tripstore.kr/inventory/travels/" + rootNode.path("id").asText())
                .itineraryJson(detailJsonStr)
                .feature(tagsBuilder.toString()) // 완성된 태그 문자열 설정
                .summary("")
                .hotelRating(0)
                .imgUrl(rootNode.path("photos").get(0).path("url").asText()) // 사진이 없을 수 있으므로 주의
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
                    .departureAirline(row[5])
                    .returnAirline(row[6])
                    .price(Integer.parseInt(row[4]))
                    .build());
        }
        return packages;
    }

    private List<TourDetailSchedule> createSchedulesFrom(JsonNode rootNode, Long tourId, Map<String, Long> cityNames) {
        List<TourDetailSchedule> schedules = new ArrayList<>();
        JsonNode dailies = rootNode.path("itinerary").path("dailies");

        // items.type 종류
        // MEAL : contents.name 저장, extra 에 조식, 중식 석식 표시되어 있음
        // PLACE : 특정 지역 도착 여부 의미. description 없음
        // SPOT_ACTIVITY : contents.description, extra 두 항목을 , 로 나누어서 description 저장
        // COLLECTION : 대표 랜드마크인듯. contents.name, contents.description 저장할것
        // ACCOMMODATION : 같은 dayNum 에 여러개 있으면 그 중 하나 간다는 얘기

        if (dailies.isArray()) {

            String place = "";

            for (JsonNode day : dailies) {
                int dayNum = day.path("dayNum").asInt();
                JsonNode items = day.path("items");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        JsonNode contents = item.path("contents");

                        // items.type 종류 별 description 설정
                        StringBuilder description = new StringBuilder();
                        String itemType = item.path("type").asText();

                        switch (itemType) {
                            case "PLACE":
                                description.append(contents.path("country").path("name").asText());
                                place = contents.path("name").asText();
                                break;
                            case "MEAL":
                                description.append(item.path("extra").asText());
                                break;
                            case "ACCOMMODATION":
                                description.append(contents.path("star").asText());
                                place = contents.path("place").path("name").asText();
                                break;
                            case "SPOT_ACTIVITY":
                                description.append(item.path("extra").asText())
                                        .append(", ")
                                        .append(contents.path("description").asText());
                                place = contents.path("place").path("name").asText();
                                break;
                            case "COLLECTION":
                                description.append(item.path("extra").asText())
                                        .append(", ")
                                        .append(contents.path("description").asText());
                                break;
                        }

                        if(!cityNames.containsKey(place)){
                            Long cityId = cityMapper.findCityIdByName(place);
                            cityNames.put(place, cityId);
                        }

                        schedules.add(TourDetailSchedule.builder()
                                .tourId(tourId)
                                .cityId(cityNames.get(place))
                                .title(contents.path("name").asText())
                                .scheduleDate(dayNum)
                                .description(description.toString())
                                .sortOrder(item.path("sort").asInt())
                                .defaultType(item.path("type").asText())
                                .build());
                    }
                }
            }
        }
        return schedules;
    }

    private List<TourCity> createCitiesForm(Long tourId, Map<String, Long> cityNames) {
        List<TourCity> cities = new ArrayList<>();

        for(Long cityId : cityNames.values()){
            TourCity tourCity = TourCity.builder()
                    .cityId(cityId)
                    .tourId(tourId)
                    .build();
            cities.add(tourCity);
        }

        return cities;
    }
}
