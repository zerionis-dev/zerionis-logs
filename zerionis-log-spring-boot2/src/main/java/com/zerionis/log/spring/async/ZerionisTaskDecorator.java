package com.zerionis.log.spring.async;

import com.zerionis.log.core.context.ZerionisContext;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * TaskDecorator that propagates MDC and ZerionisContext to async threads.
 *
 * <p>Spring Boot 2.7.x version. Functionally identical to the Spring Boot 3 version.</p>
 */
public class ZerionisTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        Map<String, Object> extraContext = ZerionisContext.getAll();

        return () -> {
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }
            if (!extraContext.isEmpty()) {
                ZerionisContext.setAll(extraContext);
            }

            try {
                runnable.run();
            } finally {
                MDC.clear();
                ZerionisContext.clear();
            }
        };
    }
}
