package tkitem.backend.global.error.exception;

import tkitem.backend.global.error.ErrorCode;

public class InvalidValueException extends BusinessException {
    public InvalidValueException(final String value) {
        super(value, ErrorCode.INVALID_INPUT_VALUE);
    }

    public InvalidValueException(final String value, final ErrorCode errorCode) {
        super(value, errorCode);
    }
}
