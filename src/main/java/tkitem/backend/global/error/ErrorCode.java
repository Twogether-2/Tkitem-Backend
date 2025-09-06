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

    /* ORDER ERROR */
    ORDER_NOT_FOUND(404, "ORDER001", "Order Not Found"),

    /* PAYMENT ERROR */
    PAYMENT_AMOUNT_MISMATCH(400, "PAYMENT001", "Payment Amount Mismatch"),
    PAYMENT_NOT_FOUND(404, "PAYMENT002", "Payment Not Found"),

    /* TOSS PAYMENTS ERROR */
    TOSS_CLIENT_ERROR(400, "TOSS001", "Invalid Payment Request"),
    TOSS_SERVER_ERROR(500, "TOSS002", "Toss Server Error"),

    /* CART ERROR */
    CART_INVALID_QUANTITY(400, "CART001", "Invalid Cart Item Quantity"),
    CART_CONCURRENCY_CONFLICT(409, "CART002", "Concurrency Conflict"),
    CART_NOT_FOUND(404, "CART003", "Cart Not Found"),
    CART_ITEM_NOT_FOUND(404, "CART004", "Cart Item Not Found"),
    CART_MISMATCH(409, "CART005", "Cart Mismatch"),
    RESERVE_FAILED(409, "CART006", "Cart Reserve Failed"),

    /* TRIP / CHECKLIST */
    TRIP_NOT_FOUND(404, "TRIP001", "Trip Not Found"),
    TRIP_PACKAGE_REQUIRED(400, "TRIP002", "Trip Package Required"),
    CHECKLIST_AI_FAILED(500, "CHECKLIST001", "Checklist AI Generation Failed"),
    AI_REASON_NOT_FOUND(404, "AI_REASON001", "AI Reason Not Found"),
    /* PREFERENCE ERROR */
    INVALID_IMAGE_URL(400, "PREFERENCE001", "Invalid Image URL"),
    AI_RESPONSE_FAILED(500, "PREFERENCE002", "AI Response Failed"),
    AI_RESPONSE_INVALID(500, "PREFERENCE003", "AI Response Invalid or Empty"),

    /* PRODUCT */
    INVALID_THEME_KEY(400, "PRODUCT001", "Invalid themeKey"),
    CATEGORY_IDS_REQUIRED(400, "PRODUCT002", "categoryIds must not be empty"),
    INVALID_CURSOR(400, "PRODUCT003", "Invalid cursor"),
    PRODUCT_QUERY_FAILED(500, "PRODUCT004", "Product query failed"),
    PRODUCT_NOT_FOUND(404, "PRODUCT005", "Product Not Found"),
    CATEGORY_MAIN_NOT_FOUND(404, "CATEGORY001", "Main Category Not Found"),
    CATEGORY_PARENT_NOT_FOUND(404, "CATEGORY002", "Parent SubCategory Not Found");

    private final int status;
    private final String code;
    private final String message;

    ErrorCode(final int status, final String code, final String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
