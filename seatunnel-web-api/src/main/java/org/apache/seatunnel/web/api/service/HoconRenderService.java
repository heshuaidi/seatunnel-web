package org.apache.seatunnel.web.api.service;

import java.util.Map;

public interface HoconRenderService {

    String render(String template, Map<String, Object> variables);

    String preview(Long taskId, Map<String, Object> overrideParams);

    String calculateHash(String hocon);
}
