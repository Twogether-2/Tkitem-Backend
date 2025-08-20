package tkitem.backend.domain.gemini.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface GeminiMapper {
    List<Map<String, Object>> findAllForEmbedding();
}
