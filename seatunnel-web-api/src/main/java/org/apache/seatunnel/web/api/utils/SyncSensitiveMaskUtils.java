package org.apache.seatunnel.web.api.utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SyncSensitiveMaskUtils {

    private static final String MASK = "******";

    private static final List<String> SENSITIVE_KEYWORDS =
            List.of("password", "passwd", "secret", "token", "key");

    private SyncSensitiveMaskUtils() {
    }

    public static Object mask(Object value) {
        if (value instanceof Map<?, ?>) {
            return maskMap((Map<?, ?>) value);
        }
        if (value instanceof Collection<?>) {
            return maskCollection((Collection<?>) value);
        }
        if (value != null && value.getClass().isArray()) {
            return maskArray(value);
        }
        return value;
    }

    public static Map<String, Object> maskMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (isSensitiveKey(key)) {
                result.put(key, MASK);
            } else {
                result.put(key, mask(entry.getValue()));
            }
        }
        return result;
    }

    private static List<Object> maskCollection(Collection<?> source) {
        List<Object> result = new ArrayList<>();
        for (Object item : source) {
            result.add(mask(item));
        }
        return result;
    }

    private static List<Object> maskArray(Object source) {
        int length = Array.getLength(source);
        List<Object> result = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            result.add(mask(Array.get(source, i)));
        }
        return result;
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lowerKey = key.toLowerCase();
        return SENSITIVE_KEYWORDS.stream().anyMatch(lowerKey::contains);
    }
}
