package com.zerionis.log.spring;

import com.zerionis.log.core.annotation.LogIgnore;
import com.zerionis.log.core.annotation.LogSkip;
import com.zerionis.log.core.context.ZerionisContext;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test controller with endpoints for integration testing.
 */
@RestController
public class TestController {

    @GetMapping("/api/hello")
    public Map<String, String> hello() {
        return Map.of("message", "hello");
    }

    @GetMapping("/api/error")
    public String error() {
        throw new RuntimeException("Test error");
    }

    @GetMapping("/api/slow")
    public Map<String, String> slow() throws InterruptedException {
        Thread.sleep(100);
        return Map.of("status", "slow");
    }

    @GetMapping("/api/extra")
    public Map<String, String> withExtra() {
        ZerionisContext.put("orderId", "ORD-123");
        ZerionisContext.put("provider", "conekta");
        return Map.of("status", "ok");
    }

    @LogIgnore
    @GetMapping("/api/ignored")
    public Map<String, String> ignored() throws InterruptedException {
        Thread.sleep(100);
        return Map.of("status", "ignored");
    }

    @LogSkip
    @GetMapping("/api/skipped")
    public Map<String, String> skipped() throws InterruptedException {
        Thread.sleep(100);
        return Map.of("status", "skipped");
    }

    @PostMapping("/api/echo")
    public Map<String, String> echo(@RequestBody Map<String, String> body) {
        return body;
    }

    @GetMapping("/actuator/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
