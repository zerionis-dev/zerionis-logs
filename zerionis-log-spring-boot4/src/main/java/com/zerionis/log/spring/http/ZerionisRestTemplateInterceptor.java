package com.zerionis.log.spring.http;

import com.zerionis.log.core.format.JsonFieldNames;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * RestTemplate interceptor that propagates tracing headers to downstream services.
 *
 * <p>Automatically injects {@code X-Trace-Id} and {@code X-Request-Id} headers
 * on every outgoing HTTP call made through a {@link org.springframework.web.client.RestTemplate}.
 * This enables distributed tracing across the Zerionis service ecosystem without
 * requiring manual header management.</p>
 *
 * <p>Auto-configured by {@code ZerionisAutoConfiguration} — applied to all
 * RestTemplate beans via RestTemplateCustomizer. Zero code changes needed.</p>
 */
public class ZerionisRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                         ClientHttpRequestExecution execution) throws IOException {
        String traceId = MDC.get(JsonFieldNames.MDC_TRACE_ID);
        if (traceId != null && !request.getHeaders().containsHeader(TRACE_ID_HEADER)) {
            request.getHeaders().set(TRACE_ID_HEADER, traceId);
        }

        String requestId = MDC.get(JsonFieldNames.MDC_REQUEST_ID);
        if (requestId != null && !request.getHeaders().containsHeader(REQUEST_ID_HEADER)) {
            request.getHeaders().set(REQUEST_ID_HEADER, requestId);
        }

        return execution.execute(request, body);
    }
}
