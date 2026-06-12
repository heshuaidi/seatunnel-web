package org.apache.seatunnel.web.api.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface HoconRenderService {

    String render(String template, Map<String, Object> variables);

    String preview(Long taskId, Map<String, Object> overrideParams);

    String calculateHash(String hocon);

    Set<String> extractVariables(String template);

    List<String> findMissingVariables(String template, Map<String, Object> variables);
}
