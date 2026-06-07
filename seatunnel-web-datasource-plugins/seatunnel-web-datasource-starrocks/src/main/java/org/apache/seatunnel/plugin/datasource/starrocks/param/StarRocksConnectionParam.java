package org.apache.seatunnel.plugin.datasource.starrocks.param;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.seatunnel.web.common.KeyValuePair;
import org.apache.seatunnel.web.common.deserializer.KeyValuePairListDeserializer;
import org.apache.seatunnel.web.spi.datasource.BaseConnectionParam;
import org.apache.seatunnel.web.spi.form.FieldType;
import org.apache.seatunnel.web.spi.form.FormField;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(callSuper = true)
public class StarRocksConnectionParam extends BaseConnectionParam {

    public static final String DEFAULT_QUERY_PORT = "9030";
    public static final String DEFAULT_HTTP_PORT = "8040";
    public static final String DEFAULT_DRIVER_LOCATION = "mysql-connector-java-8.0.29.jar";

    protected String port = DEFAULT_QUERY_PORT;

    @FormField(label = "Query Port", required = true, order = 2, defaultValue = DEFAULT_QUERY_PORT, type = FieldType.NUMBER)
    protected String queryPort = DEFAULT_QUERY_PORT;

    @FormField(label = "HTTP Port", required = true, order = 7, defaultValue = DEFAULT_HTTP_PORT, type = FieldType.NUMBER)
    protected String httpPort = DEFAULT_HTTP_PORT;

    @FormField(label = "Driver Jar", order = 6, defaultValue = DEFAULT_DRIVER_LOCATION)
    protected String driverLocation = DEFAULT_DRIVER_LOCATION;

    protected String jdbcUrl;

    protected String username;

    @FormField(
            label = "Connection Params",
            type = FieldType.CUSTOM_SELECT,
            order = 8,
            defaultValue = "[{\"key\":\"useSSL\",\"value\":\"false\"},{\"key\":\"allowPublicKeyRetrieval\",\"value\":\"true\"}]"
    )
    @JsonDeserialize(using = KeyValuePairListDeserializer.class)
    protected List<KeyValuePair> other;

    @JsonIgnore
    public Map<String, String> getOtherAsMap() {
        if (other == null) {
            return new HashMap<>();
        }
        return other.stream()
                .filter(item -> item != null && item.getKey() != null && item.getValue() != null)
                .collect(Collectors.toMap(KeyValuePair::getKey, KeyValuePair::getValue));
    }

    @Override
    public String toString() {
        return "StarRocksConnectionParam{" +
                "user='" + user + '\'' +
                ", password='" + password + '\'' +
                ", database='" + database + '\'' +
                ", url='" + url + '\'' +
                ", queryPort='" + queryPort + '\'' +
                ", httpPort='" + httpPort + '\'' +
                ", driverLocation='" + driverLocation + '\'' +
                ", driver='" + driver + '\'' +
                ", dbType=" + dbType +
                ", other=" + other +
                '}';
    }
}
