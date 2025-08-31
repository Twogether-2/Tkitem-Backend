package tkitem.backend.global.util;

public final class NumberUtil {
    public static double toDoubleOrZero(Double v) { return v == null ? 0.0 : v; }

    public static double toDoubleOrZero(Number v) { return v == null ? 0.0 : v.doubleValue(); }
}
