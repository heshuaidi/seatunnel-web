package org.apache.seatunnel.web.api.utils;

import org.apache.seatunnel.web.common.enums.SyncCheckExpectedOperator;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class SyncCheckCompareUtilsTest {

    @Test
    void eqShouldCompareNumbersByBigDecimal() {
        Assertions.assertTrue(SyncCheckCompareUtils.compare("100.0", SyncCheckExpectedOperator.EQ, "100"));
    }

    @Test
    void eqShouldCompareStringsWhenNotNumeric() {
        Assertions.assertTrue(SyncCheckCompareUtils.compare("SUCCESS", SyncCheckExpectedOperator.EQ, "SUCCESS"));
        Assertions.assertFalse(SyncCheckCompareUtils.compare("SUCCESS", SyncCheckExpectedOperator.EQ, "FAILED"));
    }

    @Test
    void shouldSupportNumericOperators() {
        Assertions.assertTrue(SyncCheckCompareUtils.compare("101", SyncCheckExpectedOperator.GT, "100"));
        Assertions.assertTrue(SyncCheckCompareUtils.compare("100", SyncCheckExpectedOperator.GE, "100"));
        Assertions.assertTrue(SyncCheckCompareUtils.compare("99", SyncCheckExpectedOperator.LT, "100"));
        Assertions.assertTrue(SyncCheckCompareUtils.compare("100", SyncCheckExpectedOperator.LE, "100"));
    }

    @Test
    void shouldSupportNullOperators() {
        Assertions.assertTrue(SyncCheckCompareUtils.compare(null, SyncCheckExpectedOperator.IS_NULL, null));
        Assertions.assertTrue(SyncCheckCompareUtils.compare("0", SyncCheckExpectedOperator.IS_NOT_NULL, null));
    }

    @Test
    void resolveExpectedValueShouldRejectMissingCompareTarget() {
        SyncCheckConfigEntity config = SyncCheckConfigEntity.builder()
                .compareToCheckCode("source_count")
                .build();

        ServiceException exception = Assertions.assertThrows(
                ServiceException.class,
                () -> SyncCheckCompareUtils.resolveExpectedValue(config, Map.of())
        );

        Assertions.assertTrue(exception.getMessage().contains("source_count"));
    }
}
