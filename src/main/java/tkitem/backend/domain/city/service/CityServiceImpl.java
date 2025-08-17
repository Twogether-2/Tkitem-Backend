package tkitem.backend.domain.city.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.city.mapper.CityMapper;
import tkitem.backend.domain.city.vo.City;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CityServiceImpl implements CityService {

    private final CityMapper cityMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void addCitiesFromJson(String filePath) throws IOException {
        List<City> cities = parseJsonFile(filePath);
        int totalCities = cities.size();
        int successCount = 0;
        int failureCount = 0;

        log.info("도시 데이터 삽입을 시작합니다. 총 {}개의 도시를 처리합니다.", totalCities);

        for (int i = 0; i < totalCities; i++) {
            City city = cities.get(i);
            try {
                cityMapper.save(city);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                log.error("도시 데이터 삽입 실패 [{} / {}]: 데이터={}, 원인={}",
                        (i + 1), totalCities, city.toString(), e.getMessage());
            }

            if ((i + 1) % 100 == 0) {
                log.info("진행 상황: {} / {} 처리 완료.", (i + 1), totalCities);
            }
        }

        log.info("도시 데이터 삽입이 완료되었습니다. [성공: {}, 실패: {}]", successCount, failureCount);
    }

    private List<City> parseJsonFile(String filePath) throws IOException {
        List<City> cities = new ArrayList<>();
        JsonNode rootNode = objectMapper.readTree(new File(filePath));
        JsonNode countries = rootNode.path("state").path("data").path("countries");

        if (countries.isArray()) {
            for (JsonNode country : countries) {
                String countryName = country.path("name").asText();
                JsonNode items = country.path("items");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String cityName = item.path("name").asText();
                        cities.add(City.builder()
                                .countryName(countryName)
                                .cityName(cityName)
                                .build());
                    }
                }
            }
        }
        return cities;
    }
}
