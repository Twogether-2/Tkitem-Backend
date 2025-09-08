package tkitem.backend.domain.city.service;

import java.io.IOException;

public interface CityService {
    void addCitiesFromJson(String filePath) throws IOException;
}
