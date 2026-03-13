package com.zerionis.log.spring.handler;

import com.zerionis.log.spring.filter.ZerionisRequestFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler that captures unhandled exceptions.
 *
 * <p>This handler does NOT emit its own log event. Instead, it stores the exception
 * in a request attribute so the {@link ZerionisRequestFilter} can include it in
 * the REQUEST_ERROR event. This prevents duplicate log entries for the same error.</p>
 *
 * <p>The exception is re-thrown to let Spring's default error handling produce
 * the appropriate HTTP error response.</p>
 */
@RestControllerAdvice
public class ZerionisExceptionHandler {

    /**
     * Catches any unhandled exception, stores it for the filter, and re-throws it.
     *
     * @param request the current HTTP request
     * @param ex      the unhandled exception
     * @throws Exception always re-throws the original exception
     */
    @ExceptionHandler(Exception.class)
    public void handleException(HttpServletRequest request, Exception ex) throws Exception {
        // Store exception in request attribute for the filter to pick up
        request.setAttribute(ZerionisRequestFilter.ERROR_ATTRIBUTE, ex);

        // Re-throw so Spring handles the response (error page, status code, etc.)
        throw ex;
    }
}
