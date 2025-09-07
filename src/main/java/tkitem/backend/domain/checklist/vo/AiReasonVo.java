package tkitem.backend.domain.checklist.vo;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class AiReasonVo {
    private Long aiReasonId;
    private Long tripId;
    private String status;       // PROCESSING, READY, ERROR
    private String contentJson;  // CLOB -> String으로 매핑
    private String ctxHash;
    private String isDeleted;    // 'T' | 'F'
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}