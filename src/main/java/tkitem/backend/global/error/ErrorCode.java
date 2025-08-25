package tkitem.backend.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    /* COMMON ERROR */
    INTERNAL_SERVER_ERROR(500, "COMMON001", "Internal Server Error"),
    INVALID_INPUT_VALUE(400, "COMMON002", "Invalid Input Value"),
    ENTITY_NOT_FOUND(404, "COMMON003", "Entity Not Found"),

    /* AUTH ERROR */
    INVALID_ACCESS_TOKEN(401, "AUTH001", "Invalid Access Token"),
    INVALID_REFRESH_TOKEN(401, "AUTH002", "Invalid Refresh Token"),
    LOGIN_FAILED(401, "AUTH003", "Login Failed"),
    RECENT_RESIGNED_MEMBER(401, "AUTH004", "Recent Resigned Member"),
    INVALID_ID_TOKEN(401, "AUTH005", "Invalid ID Token"),

    /* MEMBER ERROR */
    MEMBER_NOT_FOUND(404, "MEMBER001", "Member Not Found"),
    DUPLICATED_MEMBER(400, "MEMBER002", "Duplicated Member"),
    INVALID_MEMBER_INFO(500, "MEMBER003", "Invalid Member Info"),

    /* CART ERROR */
    CART_INVALID_QUANTITY(400, "CART001", "Invalid Cart Item Quantity"),
    CART_CONCURRENCY_CONFLICT(409, "CART002", "Concurrency Conflict"),
    CART_NOT_FOUND(404, "CART003", "Cart Not Found"),
    CART_ITEM_NOT_FOUND(404, "CART004", "Cart Item Not Found");

    private final int status;
    private final String code;
    private final String message;

    ErrorCode(final int status, final String code, final String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
