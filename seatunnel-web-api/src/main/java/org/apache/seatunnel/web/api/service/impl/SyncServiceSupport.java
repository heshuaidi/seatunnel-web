package org.apache.seatunnel.web.api.service.impl;

import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.spi.enums.Status;

import java.util.Date;

abstract class SyncServiceSupport {

    protected Date now() {
        return new Date();
    }

    protected void requireEntity(Object entity, String name) {
        if (entity == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, name);
        }
    }

    protected void requireId(Long id) {
        if (id == null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, "id");
        }
    }

    protected boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
