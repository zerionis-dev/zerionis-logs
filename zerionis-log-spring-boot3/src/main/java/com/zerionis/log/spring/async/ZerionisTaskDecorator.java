package com.zerionis.log.spring.async;

import com.zerionis.log.core.context.ZerionisContext;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * TaskDecorator that propagates MDC and ZerionisContext to async threads.
 *
 * <p>When using {@code @Async} methods, Spring runs them in a separate thread pool.
 * Without this decorator, the traceId, requestId, and custom extra fields set
 * by the request filter are lost in the async thread.</p>
 *
 * <p>This decorator captures the MDC and ZerionisContext from the calling thread,
 * then restores them before the async task executes, and cleans up after.</p>
 *
 * <p>Auto-configured by {@code ZerionisAutoConfiguration} — applied to the default
 * {@code ThreadPoolTaskExecutor} via TaskExecutorCustomizer. Zero code changes needed.</p>
 */
public class ZerionisTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // Capture context from the calling thread
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        Map<String, Object> extraContext = ZerionisContext.getAll();

        return () -> {
            // Restore context in the async thread
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }
            if (!extraContext.isEmpty()) {
                ZerionisContext.setAll(extraContext);
            }

            try {
                runnable.run();
            } finally {
                // Clean up to prevent leaks in thread pool threads
                MDC.clear();
                ZerionisContext.clear();
            }
        };
    }
}
