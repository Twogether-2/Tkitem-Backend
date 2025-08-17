//package tkitem.backend.global.error.exception;
//
//import org.springframework.security.core.AuthenticationException;
//
//import lombok.Getter;
//import tkitem.backend.global.error.ErrorCode;
//
//@Getter
//public class InvalidTokenException extends AuthenticationException {
//    private final ErrorCode errorCode;
//
//    public InvalidTokenException(ErrorCode errorCode) {
//        super(errorCode.getMessage());
//        this.errorCode = errorCode;
//    }
//
//    public InvalidTokenException(ErrorCode errorCode, Throwable cause) {
//        super(errorCode.getMessage(), cause);
//        this.errorCode = errorCode;
//    }
//
//}
