package org.apache.seatunnel.web.core.time;

import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.seatunnel.plugin.datasource.api.hocon.DataSourceHoconBuilder;
import org.apache.seatunnel.web.common.enums.TimeVariableValueType;
import org.apache.seatunnel.web.core.exceptions.ServiceException;
import org.apache.seatunnel.web.dao.entity.TimeVariable;
import org.apache.seatunnel.web.spi.bean.dto.TimeVariableRenderReq;
import org.apache.seatunnel.web.spi.bean.dto.config.JobScheduleConfig;
import org.apache.seatunnel.web.spi.bean.vo.TimeVariableRenderVO;
import org.apache.seatunnel.web.spi.enums.Status;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TimeVariableJdbcSqlRenderService {

    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile("\\$\\{(?:var:)?([a-zA-Z][a-zA-Z0-9_]*)}");

    private static final String DEFAULT_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Resource
    private TimeVariableRenderService timeVariableRenderService;

    @Resource
    private TimeExpressionEvaluator timeExpressionEvaluator;

    public String renderSql(String sql,
                            DataSourceHoconBuilder hoconBuilder,
                            JobScheduleConfig scheduleConfig) {
        if (StringUtils.isBlank(sql)) {
            return sql;
        }

        Set<String> referencedVariables = extractReferencedVariables(sql);
        if (referencedVariables.isEmpty()) {
            return sql;
        }

        Map<String, TimeVariable> variableNameMap = loadEnabledVariableNameMap();
        validateVariablesExistsInDatabase(referencedVariables, variableNameMap);

        Map<String, JobScheduleConfig.ScheduleParamItem> scheduleParamIdMap =
                buildScheduleParamIdMap(scheduleConfig);

        validateVariablesConfiguredInSchedule(
                referencedVariables,
                variableNameMap,
                scheduleParamIdMap
        );

        TimeVariableRenderVO renderVO = renderVariablesByScheduleParams(
                sql,
                referencedVariables,
                variableNameMap,
                scheduleParamIdMap
        );

        validateUnresolvedVariables(renderVO);

        return renderSqlLiteral(
                sql,
                renderVO,
                variableNameMap,
                hoconBuilder
        );
    }

    private TimeVariableRenderVO renderVariablesByScheduleParams(
            String sql,
            Set<String> referencedVariables,
            Map<String, TimeVariable> variableNameMap,
            Map<String, JobScheduleConfig.ScheduleParamItem> scheduleParamIdMap) {

        Map<String, String> scheduleVariables = renderScheduleParams(scheduleParamIdMap, variableNameMap);
        Map<String, String> overrideVariables = referencedVariables.stream()
                .filter(scheduleVariables::containsKey)
                .collect(Collectors.toMap(
                        item -> item,
                        scheduleVariables::get,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        TimeVariableRenderReq req = new TimeVariableRenderReq();
        req.setContent(sql);
        req.setOverrideVariables(overrideVariables);
        return timeVariableRenderService.render(req);
    }

    public Map<String, String> renderScheduleParams(JobScheduleConfig scheduleConfig) {
        return renderScheduleParams(
                buildScheduleParamIdMap(scheduleConfig),
                loadEnabledVariableNameMap()
        );
    }

    private Map<String, String> renderScheduleParams(
            Map<String, JobScheduleConfig.ScheduleParamItem> scheduleParamIdMap,
            Map<String, TimeVariable> variableNameMap) {

        if (scheduleParamIdMap == null || scheduleParamIdMap.isEmpty()
                || variableNameMap == null || variableNameMap.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, TimeVariable> variableIdMap = variableNameMap.values()
                .stream()
                .filter(item -> item != null && item.getId() != null)
                .collect(Collectors.toMap(
                        item -> String.valueOf(item.getId()),
                        item -> item,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        Map<String, String> result = new LinkedHashMap<>();
        LocalDateTime baseTime = LocalDateTime.now();

        for (JobScheduleConfig.ScheduleParamItem scheduleParam : scheduleParamIdMap.values()) {
            if (scheduleParam == null || StringUtils.isBlank(scheduleParam.getParamName())) {
                continue;
            }

            String paramName = scheduleParam.getParamName().trim();
            TimeVariable variable = variableIdMap.get(paramName);
            if (variable == null) {
                variable = variableNameMap.get(paramName);
            }
            if (variable == null || StringUtils.isBlank(variable.getParamName())) {
                continue;
            }

            result.put(variable.getParamName(), renderScheduleParamValue(scheduleParam, variable, baseTime));
        }

        return result;
    }

    private String renderSqlLiteral(String originalSql,
                                    TimeVariableRenderVO renderVO,
                                    Map<String, TimeVariable> variableNameMap,
                                    DataSourceHoconBuilder hoconBuilder) {
        Map<String, TimeVariableRenderVO.VariableItem> renderedVariableMap =
                renderVO.getVariables() == null
                        ? Collections.emptyMap()
                        : renderVO.getVariables()
                        .stream()
                        .collect(Collectors.toMap(
                                TimeVariableRenderVO.VariableItem::getName,
                                item -> item,
                                (first, second) -> first
                        ));

        Matcher matcher = VARIABLE_PATTERN.matcher(originalSql);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String variableName = matcher.group(1);

            TimeVariableRenderVO.VariableItem renderedVariable = renderedVariableMap.get(variableName);
            TimeVariable dbVariable = variableNameMap.get(variableName);

            if (renderedVariable == null || dbVariable == null) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            String sqlLiteral = hoconBuilder.renderTimeLiteral(
                    renderedVariable.getValue(),
                    dbVariable.getTimeFormat()
            );

            matcher.appendReplacement(buffer, Matcher.quoteReplacement(sqlLiteral));
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private Set<String> extractReferencedVariables(String sql) {
        Matcher matcher = VARIABLE_PATTERN.matcher(sql);
        Set<String> variables = new LinkedHashSet<>();

        while (matcher.find()) {
            variables.add(matcher.group(1));
        }

        return variables;
    }

    private Map<String, TimeVariable> loadEnabledVariableNameMap() {
        List<TimeVariable> variables = timeVariableRenderService.getAllEnabledVariables();

        if (CollectionUtils.isEmpty(variables)) {
            return Collections.emptyMap();
        }

        return variables.stream()
                .filter(item -> StringUtils.isNotBlank(item.getParamName()))
                .collect(Collectors.toMap(
                        TimeVariable::getParamName,
                        item -> item,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }

    private Map<String, JobScheduleConfig.ScheduleParamItem> buildScheduleParamIdMap(
            JobScheduleConfig scheduleConfig) {
        if (scheduleConfig == null || CollectionUtils.isEmpty(scheduleConfig.getParamsList())) {
            return Collections.emptyMap();
        }

        return scheduleConfig.getParamsList()
                .stream()
                .filter(item -> item != null && StringUtils.isNotBlank(item.getParamName()))
                .collect(Collectors.toMap(
                        JobScheduleConfig.ScheduleParamItem::getParamName,
                        item -> item,
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
    }

    private void validateVariablesExistsInDatabase(Set<String> referencedVariables,
                                                   Map<String, TimeVariable> variableNameMap) {
        List<String> notExists = referencedVariables.stream()
                .filter(name -> !variableNameMap.containsKey(name))
                .collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(notExists)) {
            throw new ServiceException(
                    Status.REQUEST_PARAMS_NOT_VALID_ERROR,
                    "存在未定义或未启用的时间变量：" + notExists
            );
        }
    }

    private void validateVariablesConfiguredInSchedule(
            Set<String> referencedVariables,
            Map<String, TimeVariable> variableNameMap,
            Map<String, JobScheduleConfig.ScheduleParamItem> scheduleParamIdMap) {
        List<String> notConfigured = referencedVariables.stream()
                .filter(variableName -> {
                    TimeVariable variable = variableNameMap.get(variableName);
                    if (variable == null || variable.getId() == null) {
                        return true;
                    }

                    return !scheduleParamIdMap.containsKey(String.valueOf(variable.getId()))
                            && !scheduleParamIdMap.containsKey(variableName);
                })
                .collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(notConfigured)) {
            throw new ServiceException(
                    Status.REQUEST_PARAMS_NOT_VALID_ERROR,
                    "SQL 中引用的时间变量未在当前任务调度参数中配置：" + notConfigured
            );
        }
    }

    private String resolveScheduleExpression(JobScheduleConfig.ScheduleParamItem scheduleParam,
                                             TimeVariable variable,
                                             String variableName) {
        String expression = scheduleParam == null ? null : scheduleParam.getParamValue();

        if (StringUtils.isBlank(expression)) {
            expression = variable.getExpression();
        }

        if (StringUtils.isBlank(expression)) {
            expression = variable.getDefaultValue();
        }

        if (StringUtils.isBlank(expression)) {
            throw new ServiceException(
                    Status.REQUEST_PARAMS_NOT_VALID_ERROR,
                    "时间变量未配置有效表达式：" + variableName
            );
        }

        return expression.trim();
    }

    private String renderScheduleParamValue(JobScheduleConfig.ScheduleParamItem scheduleParam,
                                            TimeVariable variable,
                                            LocalDateTime baseTime) {
        if (TimeVariableValueType.FIXED.name().equals(variable.getValueType())) {
            String value = firstNonBlank(
                    scheduleParam == null ? null : scheduleParam.getParamValue(),
                    variable.getDefaultValue()
            );
            if (StringUtils.isBlank(value)) {
                throw new ServiceException(
                        Status.REQUEST_PARAMS_NOT_VALID_ERROR,
                        "固定值变量未配置默认值：" + variable.getParamName()
                );
            }
            return value;
        }

        if (TimeVariableValueType.DYNAMIC.name().equals(variable.getValueType())) {
            String expression = resolveScheduleExpression(scheduleParam, variable, variable.getParamName());
            String timeFormat = StringUtils.isNotBlank(variable.getTimeFormat())
                    ? variable.getTimeFormat()
                    : DEFAULT_TIME_FORMAT;
            try {
                return timeExpressionEvaluator.evaluateToString(expression, timeFormat, baseTime);
            } catch (Exception e) {
                throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR, e.getMessage());
            }
        }

        throw new ServiceException(
                Status.REQUEST_PARAMS_NOT_VALID_ERROR,
                "不支持的变量取值方式：" + variable.getValueType()
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private void validateUnresolvedVariables(TimeVariableRenderVO renderVO) {
        if (renderVO == null) {
            throw new ServiceException(
                    Status.REQUEST_PARAMS_NOT_VALID_ERROR,
                    "时间变量渲染失败"
            );
        }

        if (CollectionUtils.isNotEmpty(renderVO.getUnresolvedVariables())) {
            throw new ServiceException(
                    Status.REQUEST_PARAMS_NOT_VALID_ERROR,
                    "存在未解析的时间变量：" + renderVO.getUnresolvedVariables()
            );
        }
    }
}
