package tkitem.backend.domain.scheduleType.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tkitem.backend.domain.scheduleType.mapper.TourScheduleTypeMapper;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TourLabelWriter {
    private final TourScheduleTypeMapper tstMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveLabelsChunk(List<Map<String, Object>> labelsToSave) {
        for (var m : labelsToSave) {
            Long tdsId  = (Long)  m.get("tdsId");
            Long typeId = (Long)  m.get("typeId");
            Double score= (Double) m.get("score");
            tstMapper.upsertTourScheduleType(tdsId, typeId, score);
        }
    }
}
