package tkitem.backend.domain.tour.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.city.mapper.CityMapper;
import tkitem.backend.domain.city.vo.City;
import tkitem.backend.domain.tour.mapper.TourMapper;
import tkitem.backend.domain.tour.vo.Tour;
import tkitem.backend.domain.tour.vo.TourCity;
import tkitem.backend.domain.tour.vo.TourDetailSchedule;
import tkitem.backend.domain.tour.vo.TourPackage;

import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

    @Override
    public void loadDataFromCsv(String filePath) throws Exception {
        log.info("JSON 데이터 적재를 시작합니다. 파일 경로: {}", filePath);

        try (FileReader reader = new FileReader(filePath)) {
            JsonNode rootNode = objectMapper.readTree(reader);

            if (rootNode.isArray()) {
                int processedCount = 0;
                for (JsonNode tripNode : rootNode) {
                    // 테스트를 위해 100개 항목만 처리
//                    if (processedCount >= 30) {
//                        log.info("30개 항목만 테스트하고 종료합니다.");
//                        break;
//                    }

                    String tripCode = tripNode.path("tripCode").asText(null);
                    if (!isValidTripCode(tripCode)) {
                        log.warn("유효하지 않은 tripCode 형식입니다. 건너뜁니다: {}", tripCode);
                        continue;
                    }

                    JsonNode detailJsonNode = tripNode.path("detail_json");
                    if (detailJsonNode.isMissingNode() || detailJsonNode.isEmpty()) {
                        log.warn("detail_json이 비어있습니다. tripCode: {}. 건너뜁니다.", tripCode);
                        continue;
                    }

                    JsonNode tourPackageNode = tripNode.path("tourPackage");
                    if (!tourPackageNode.isArray() || tourPackageNode.isEmpty()) {
                        log.warn("tourPackage 배열이 비어있거나 없습니다. tripCode: {}. 건너뜁니다.", tripCode);
                        continue;
                    }

                    processTripCodeGroup(tripCode, detailJsonNode, tourPackageNode);
                    processedCount++;
                }
            }
        } catch (IOException e) {
            log.error("JSON 파일을 읽는 중 오류가 발생했습니다.", e);
            throw e;
        }

        log.info("JSON 데이터 적재를 완료했습니다.");
    }

    @Transactional
    public void processTripCodeGroup(String tripCode, JsonNode detailJsonNode, JsonNode tourPackageNode) {
        try {
            Tour existingTour = tourMapper.findTourByTripCode(tripCode);

            if (existingTour == null) {
                // 신규 Tour: Tour, TourCity, TourDetailSchedule, 모든 TourPackage 삽입
                log.info("신규 tripCode='{}' 입니다. 전체 데이터를 삽입합니다.", tripCode);

                String detailJsonStr = objectMapper.writeValueAsString(detailJsonNode);

                // 도시 이름 수집
                Map<String, Long> cityNames = collectCityNames(detailJsonNode);

                // 첫 도시, 나라이름 수집
                JsonNode placeNode = detailJsonNode.path("place");
                String startCity = "";
                String startCountry = "";
                if (placeNode.isArray() && placeNode.size() > 0) {
                    startCity = placeNode.get(0).get("name").asText("NONE");
                    startCountry = placeNode.get(0).get("country").get("name").asText("NONE");
                }

                // Tour 객체 생성 및 삽입
                Tour tour = createTourFrom(tripCode, detailJsonNode, detailJsonStr);
                tourMapper.insertTour(tour);
                Long generatedTourId = tour.getTourId();

                // TourDetailSchedule 리스트 생성 및 삽입
                List<TourDetailSchedule> schedules = createSchedulesFrom(detailJsonNode, generatedTourId, cityNames, startCity, startCountry);
                if (!schedules.isEmpty()) {
                    for (TourDetailSchedule schedule : schedules) {
                        tourMapper.insertTourDetailSchedule(schedule);
                    }
                }

                // TourCity 리스트 생성 및 삽입
                List<TourCity> tourCityList = createCitiesForm(generatedTourId, cityNames);
                if (!tourCityList.isEmpty()) {
                    for (TourCity tourCity : tourCityList) {
                        tourMapper.insertTourCity(tourCity);
                    }
                }

                // TourPackage 리스트 생성 및 삽입
                List<TourPackage> tourPackages = createTourPackagesFrom(tourPackageNode, generatedTourId, tripCode);
                if (!tourPackages.isEmpty()) {
                    for (TourPackage tourPackage : tourPackages) {
                        tourMapper.insertTourPackage(tourPackage);
                    }
                }

                log.info("tripCode '{}' 처리 완료 (Tour 1개, Package {}개, Schedule {}개)",
                        tripCode, tourPackages.size(), schedules.size());
                log.info("SUCCESSFULLY PROCESSED tripCode: {}", tripCode);

            } else {
                // 기존 Tour: 신규 TourPackage만 확인하여 추가
                log.info("기존 tripCode='{}' 입니다. 신규 TourPackage만 확인하여 추가합니다.", tripCode);
                Long tourId = existingTour.getTourId();

                // DB에 저장된 travelId(packageDateCode) 목록 조회
                Set<String> existingPackageDateCodes = new HashSet<>(tourMapper.findPackageDateCodesByTourId(tourId));

                List<TourPackage> newTourPackages = new ArrayList<>();
                for (JsonNode packageNode : tourPackageNode) {
                    String travelId = packageNode.path("travelId").asText(null);
                    if (travelId != null && !existingPackageDateCodes.contains(travelId)) {
                        newTourPackages.add(createSingleTourPackageFrom(packageNode, tourId, tripCode));
                    }
                }

                if (!newTourPackages.isEmpty()) {
                    for (TourPackage tourPackage : newTourPackages) {
                        tourMapper.insertTourPackage(tourPackage);
                    }
                    log.info("{}개의 신규 TourPackage를 tripCode '{}'에 추가했습니다.", newTourPackages.size(), tripCode);
                } else {
                    log.info("tripCode '{}'에 대한 신규 TourPackage가 없습니다.", tripCode);
                }
            }
        } catch (Exception e) {
            log.error("tripCode '{}' 처리 중 오류가 발생하여 건너뜁니다.", tripCode, e);
            // @Transactional에 의해 이 tripCode에 대한 모든 DB 작업은 롤백됨
        }
    }

    private Map<String, Long> collectCityNames(JsonNode detailJsonNode) {
        Map<String, Long> cityNames = new HashMap<>();
        JsonNode placesNode = detailJsonNode.get("places");
        if (placesNode != null && placesNode.isArray()) {
            for (JsonNode place : placesNode) {
                String cityName = place.get("name").asText();
                String countryName = place.get("country").get("name").asText();
                if (cityName != null && !cityName.isEmpty()) {
                    Long cityId = getOrCreateCityId(cityName, countryName);
                    if (cityId != null) {
                        cityNames.put(cityName, cityId);
                    } else {
                        log.warn("도시생성, 조회 실패: {} ({})", cityName, countryName);
                    }
                }
            }
        }
        return cityNames;
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
                .itineraryJson(detailJsonStr)
                .feature(tagsBuilder.toString()) // 완성된 태그 문자열 설정
                .summary("")
                .hotelRating(0)
                .imgUrl(rootNode.path("photos").get(0).path("url").asText()) // 사진이 없을 수 있으므로 주의
                .build();
    }

    private List<TourPackage> createTourPackagesFrom(JsonNode tourPackageNode, Long tourId, String tripCode) {
        List<TourPackage> packages = new ArrayList<>();
        if (tourPackageNode.isArray()) {
            for (JsonNode packageNode : tourPackageNode) {
                packages.add(createSingleTourPackageFrom(packageNode, tourId, tripCode));
            }
        }
        return packages;
    }

    private TourPackage createSingleTourPackageFrom(JsonNode packageNode, Long tourId, String tripCode) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        String travelId = packageNode.path("travelId").asText();
        String departureDateTimeStr = packageNode.path("departureDateTime").asText(null);
        String arrivalDateTimeStr = packageNode.path("arrivalDateTime").asText(null);

        LocalDateTime departureDateTime = null;
        if (departureDateTimeStr != null && !departureDateTimeStr.isEmpty()) {
            try {
                departureDateTime = LocalDateTime.parse(departureDateTimeStr, formatter);
            } catch (DateTimeParseException e) {
                log.warn("출발 날짜 형식이 잘못되었습니다: '{}', tripCode: {}", departureDateTimeStr, tripCode);
            }
        }

        LocalDateTime arrivalDateTime = null;
        if (arrivalDateTimeStr != null && !arrivalDateTimeStr.isEmpty()) {
            try {
                arrivalDateTime = LocalDateTime.parse(arrivalDateTimeStr, formatter);
            } catch (DateTimeParseException e) {
                log.warn("도착 날짜 형식이 잘못되었습니다: '{}', tripCode: {}", arrivalDateTimeStr, tripCode);
            }
        }

        return TourPackage.builder()
                .tourId(tourId)
                .packageDateCode(travelId)
                .departureDate(departureDateTime != null ? departureDateTime.toLocalDate() : null)
                .returnDate(arrivalDateTime != null ? arrivalDateTime.toLocalDate() : null)
                .departureAirline(packageNode.path("departureAirline").asText(null))
                .returnAirline(packageNode.path("returnAirline").asText(null))
                .price(packageNode.path("sellingPrice").asInt())
                .bookingUrl("https://tripstore.thehyundaitravel.com/products/" + tripCode + "?travelId=" + travelId)
                .sourceUrl("https://api.tripstore.kr/inventory/travels/" + travelId)
                .build();
    }

    private List<TourDetailSchedule> createSchedulesFrom(JsonNode rootNode, Long tourId, Map<String, Long> cityNames, String startCity, String startCountry) {
        List<TourDetailSchedule> schedules = new ArrayList<>();
        JsonNode dailies = rootNode.path("itinerary").path("dailies");

        // items.type 종류
        // MEAL : contents.name 저장, extra 에 조식, 중식 석식 표시되어 있음
        // PLACE : 특정 지역 도착 여부 의미. description 없음
        // SPOT_ACTIVITY : contents.description, extra 두 항목을 , 로 나누어서 description 저장
        // COLLECTION : 대표 랜드마크인듯. contents.name, contents.description 저장할것
        // ACCOMMODATION : 같은 dayNum 에 여러개 있으면 그 중 하나 간다는 얘기

        if (dailies.isArray()) {

            String place = startCity;
            String country = startCountry;

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
                                place = contents.path("name").asText();
                                country = contents.path("country").path("name").asText();
                                description.append(country);
                                break;
                            case "MEAL":
                                description.append(item.path("extra").asText());
                                break;
                            case "ACCOMMODATION":
                                description.append(contents.path("star").asText());
                                place = contents.path("place").path("name").asText();
                                country = contents.path("place").path("country").path("name").asText();
                                break;
                            case "SPOT_ACTIVITY":
                                description.append(item.path("extra").asText())
                                        .append(!item.path("extra").asText().isEmpty() && !contents.path("description").asText().isEmpty() ? ", " : "")
                                        .append(contents.path("description").asText());
                                place = contents.path("place").path("name").asText();
                                country = contents.path("place").path("country").path("name").asText();
                                break;
                            case "COLLECTION":
                                description.append(item.path("extra").asText())
                                        .append(!item.path("extra").asText().isEmpty() && !contents.path("description").asText().isEmpty() ? ", " : "")
                                        .append(contents.path("description").asText());
                                break;
                        }

                        // 문제상황 :
                        // city_name = 인천, country_name =  일본  --> 그냥 데이터가 문제
                        // null, null 이 들어가는 경우 --> json 데이터에서 items 내에 PLACE 보다 MEAL이 먼저 나옴

                        if (!cityNames.containsKey(place)) {
                            Long cityId = getOrCreateCityId(place, country);
                            if (cityId != null) {
                                cityNames.put(place, cityId);
                            } else {
                                log.warn("도시 생성/조회 실패: {} ({})", place, country);
                            }
                        }

                        schedules.add(TourDetailSchedule.builder()
                                .tourId(tourId)
                                .cityId(cityNames.get(place))
                                .title(contents.path("name").asText())
                                .scheduleDate(dayNum)
                                .description(description.toString())
                                .sortOrder(item.path("sort").asInt())
                                .defaultType(itemType)
                                .build());
                    }
                }
            }
        }
        return schedules;
    }

    private List<TourCity> createCitiesForm(Long tourId, Map<String, Long> cityNames) {
        List<TourCity> cities = new ArrayList<>();

        for (Long cityId : cityNames.values()) {
            if (cityId != null) { // Null check
                TourCity tourCity = TourCity.builder()
                        .cityId(cityId)
                        .tourId(tourId)
                        .build();
                cities.add(tourCity);
            }
        }
        return cities;
    }

    private Long getOrCreateCityId(String cityName, String countryName) {
        Long id = cityMapper.findCityIdByName(cityName, countryName).orElse(null);
        if (id != null) {
            return id;
        }

        // id == null, new city creation logic
        System.out.println("\n========================================================");
        log.info("새로운 도시를 데이터베이스에 추가해야 합니다.");
            log.info("데이터 값: 도시명='{}', 국가명='{}'", cityName, countryName);
        System.out.println("--------------------------------------------------------");
        System.out.println("선택하세요:");
        System.out.println("  1: 위 값으로 도시를 생성합니다.");
        System.out.println("  2: 새로운 값을 직접 입력하여 도시를 생성합니다.");
        System.out.print("입력 (1 또는 2): ");

        Scanner scanner = new Scanner(System.in);
        String choice = scanner.nextLine();

        String finalCityName = cityName;
        String finalCountryName = countryName;

        if ("2".equals(choice)) {
            System.out.print("새로운 도시명을 입력하세요: ");
            finalCityName = scanner.nextLine();
            System.out.print("새로운 국가명을 입력하세요: ");
            finalCountryName = scanner.nextLine();
            System.out.println("========================================================");

            // 사용자가 직접 입력한 값으로 도시가 이미 존재하는지 다시 확인
            Long manuallyEnteredCityId = cityMapper.findCityIdByName(finalCityName, finalCountryName).orElse(null);
            if (manuallyEnteredCityId != null) {
                log.info("입력한 도시는 이미 존재합니다. 기존 ID를 사용합니다: {}", manuallyEnteredCityId);
                return manuallyEnteredCityId;
            }
        } else {
            log.info("전달받은 값으로 생성을 진행합니다.");
            System.out.println("========================================================");
        }

        City city = City.builder()
                .cityName(finalCityName)
                .countryName(finalCountryName)
                .build();

        try {
            cityMapper.save(city);
            log.info("새로운 도시 생성 완료: '{}' ({}), ID: {}", city.getCityName(), city.getCountryName(), city.getCityId());
            return city.getCityId();
        } catch (DuplicateKeyException e) {
            log.warn("도시 생성 중복 예외 발생. 다른 트랜잭션에서 생성되었을 수 있습니다. 기존 도시 ID를 조회합니다: {} ({})", finalCityName, finalCountryName);
            return cityMapper.findCityIdByName(finalCityName, finalCountryName)
                    .orElse(null); // 실패 시 null 반환
        }
    }
}
