package org.apache.seatunnel.web.api.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class SyncSensitiveMaskUtilsTest {

    @Test
    void maskShouldHideSensitiveKeys() {
        Map<String, Object> masked = SyncSensitiveMaskUtils.maskMap(Map.of(
                "username", "etl",
                "password", "secret",
                "sourcePassword", "source-secret",
                "starrocksPassword", "starrocks-secret",
                "apiToken", "token-value",
                "nested", Map.of("clientSecret", "secret-value"),
                "items", List.of(Map.of("accessKey", "key-value"))
        ));

        Assertions.assertEquals("etl", masked.get("username"));
        Assertions.assertEquals("******", masked.get("password"));
        Assertions.assertEquals("******", masked.get("sourcePassword"));
        Assertions.assertEquals("******", masked.get("starrocksPassword"));
        Assertions.assertEquals("******", masked.get("apiToken"));

        Map<?, ?> nested = (Map<?, ?>) masked.get("nested");
        Assertions.assertEquals("******", nested.get("clientSecret"));

        List<?> items = (List<?>) masked.get("items");
        Map<?, ?> firstItem = (Map<?, ?>) items.get(0);
        Assertions.assertEquals("******", firstItem.get("accessKey"));
    }
}
