package tkitem.backend.domain.checklist.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiReasonEnvelope {
    // READY | PROCESSING | ERROR
    private String status;
    // READY일 때만 채움
    private AiReasonResponse data;
    // ERROR일 때만 안내 메시지
    private String message;

    public static AiReasonEnvelope ready(AiReasonResponse data) {
        return new AiReasonEnvelope("READY", data, null);
    }
    public static AiReasonEnvelope processing() {
        return new AiReasonEnvelope("PROCESSING", null, null);
    }
    public static AiReasonEnvelope error(String msg) {
        return new AiReasonEnvelope("ERROR", null, msg);
    }
}
