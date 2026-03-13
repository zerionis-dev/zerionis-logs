package com.zerionis.log.core.format;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zerionis.log.core.model.ZerionisLogEvent;

/**
 * Serializes {@link ZerionisLogEvent} instances to JSON strings.
 *
 * Central serializer for the library. Every log event passes through here
 * to become structured JSON.
 *
 * <p>Configuration:</p>
 * <ul>
 *   <li>Dates as ISO 8601 strings, not epoch timestamps</li>
 *   <li>Null fields excluded to reduce log size</li>
 *   <li>Thread-safe (ObjectMapper is reused across threads)</li>
 * </ul>
 */
public class ZerionisLogFormatter {

    private final ObjectMapper objectMapper;

    /**
     * Creates a formatter with an ObjectMapper configured for log serialization.
     */
    public ZerionisLogFormatter() {
        this.objectMapper = new ObjectMapper();

        // Support java.time types (Instant → ISO 8601 string)
        this.objectMapper.registerModule(new JavaTimeModule());

        // Output "2026-03-10T14:32:01.123Z" instead of 1741614721123
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Skip null fields to keep logs compact
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * Serializes an event to a single-line JSON string.
     * One line = one log event (required by CloudWatch, Filebeat, Fluentd).
     *
     * @param event the log event to serialize
     * @return compact JSON string
     * @throws JsonProcessingException if serialization fails
     */
    public String format(ZerionisLogEvent event) throws JsonProcessingException {
        return objectMapper.writeValueAsString(event);
    }

    /**
     * Serializes an event to a pretty-printed JSON string.
     * Intended for development/debugging only. Do not use in production
     * (output is 3-5x larger).
     *
     * @param event the log event to serialize
     * @return indented JSON string
     * @throws JsonProcessingException if serialization fails
     */
    public String formatPretty(ZerionisLogEvent event) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event);
    }

    /**
     * Serializes an event, returning a fallback JSON on failure.
     * This method never throws. Used inside the Logback layout where an exception
     * could break the entire logging pipeline.
     *
     * @param event the log event to serialize
     * @return JSON string, or a minimal error JSON if serialization fails
     */
    public String formatSafe(ZerionisLogEvent event) {
        try {
            return format(event);
        } catch (JsonProcessingException e) {
            return "{\"level\":\"ERROR\",\"message\":\"zerionis-log: serialization error: "
                    + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
