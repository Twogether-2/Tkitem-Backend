package tkitem.backend.domain.city.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import tkitem.backend.domain.city.vo.City;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Mapper
public interface CityMapper {
    void save(City city);

    List<City> findCitiesByName(@Param("names")Set<String> names);

    Optional<Long> findCityIdByName(@Param("name") String name, @Param("countryName") String countryName);

    List<City> findCitiesByTourPackageId(Long tourPackageId);
}
