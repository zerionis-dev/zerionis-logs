package com.zerionis.log.spring.handler;

import com.zerionis.log.spring.filter.ZerionisRequestFilter;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for Spring Boot 2.7.x.
 *
 * <p>Stores the exception in a request attribute for the filter to include
 * in the REQUEST_ERROR event. Does not emit its own log to prevent duplicates.</p>
 */
@RestControllerAdvice
public class ZerionisExceptionHandler {

    @ExceptionHandler(Exception.class)
    public void handleException(HttpServletRequest request, Exception ex) throws Exception {
        request.setAttribute(ZerionisRequestFilter.ERROR_ATTRIBUTE, ex);
        throw ex;
    }
}
