package org.apache.seatunnel.web.api.utils;

import org.apache.seatunnel.web.common.enums.SyncCheckExpectedOperator;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.SyncCheckConfigEntity;

import java.math.BigDecimal;
import java.util.Map;

public final class SyncCheckCompareUtils {

    private SyncCheckCompareUtils() {
    }

    public static boolean compare(String actualValue, SyncCheckExpectedOperator operator, String expectedValue) {
        if (operator == null) {
            return true;
        }
        switch (operator) {
            case IS_NULL:
                return actualValue == null;
            case IS_NOT_NULL:
                return actualValue != null;
            case EQ:
                return equalsValue(actualValue, expectedValue);
            case NE:
                return !equalsValue(actualValue, expectedValue);
            case GT:
                return numericCompare(actualValue, expectedValue) > 0;
            case GE:
                return numericCompare(actualValue, expectedValue) >= 0;
            case LT:
                return numericCompare(actualValue, expectedValue) < 0;
            case LE:
                return numericCompare(actualValue, expectedValue) <= 0;
            default:
                throw new ServiceException("Unsupported check expected operator: " + operator);
        }
    }

    public static String resolveExpectedValue(
            SyncCheckConfigEntity config,
            Map<String, String> actualValueByCheckCode
    ) {
        String compareToCheckCode = config.getCompareToCheckCode();
        if (compareToCheckCode == null || compareToCheckCode.trim().isEmpty()) {
            return config.getExpectedValue();
        }
        if (actualValueByCheckCode == null || !actualValueByCheckCode.containsKey(compareToCheckCode)) {
            throw new ServiceException("Compare target check result not found: " + compareToCheckCode);
        }
        return actualValueByCheckCode.get(compareToCheckCode);
    }

    public static boolean toBoolean(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        try {
            return new BigDecimal(normalized).compareTo(BigDecimal.ZERO) != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean equalsValue(String actualValue, String expectedValue) {
        if (actualValue == null || expectedValue == null) {
            return actualValue == null && expectedValue == null;
        }
        BigDecimal actualNumber = parseNumber(actualValue);
        BigDecimal expectedNumber = parseNumber(expectedValue);
        if (actualNumber != null && expectedNumber != null) {
            return actualNumber.compareTo(expectedNumber) == 0;
        }
        return actualValue.equals(expectedValue);
    }

    private static int numericCompare(String actualValue, String expectedValue) {
        BigDecimal actualNumber = parseNumber(actualValue);
        BigDecimal expectedNumber = parseNumber(expectedValue);
        if (actualNumber == null || expectedNumber == null) {
            throw new ServiceException("Numeric check requires numeric actual and expected values");
        }
        return actualNumber.compareTo(expectedNumber);
    }

    private static BigDecimal parseNumber(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
