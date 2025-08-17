package tkitem.backend.domain.city.mapper;

import org.apache.ibatis.annotations.Mapper;
import tkitem.backend.domain.city.vo.City;

@Mapper
public interface CityMapper {
    void save(City city);
}
