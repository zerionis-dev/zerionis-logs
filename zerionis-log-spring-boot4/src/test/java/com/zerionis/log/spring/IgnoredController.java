package com.zerionis.log.spring;

import com.zerionis.log.core.annotation.LogIgnore;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller annotated with @LogIgnore at class level.
 * All methods in this controller should be skipped by the aspect.
 */
@LogIgnore
@RestController
public class IgnoredController {

    @GetMapping("/api/class-ignored")
    public Map<String, String> classIgnored() throws InterruptedException {
        Thread.sleep(100);
        return Map.of("status", "class-ignored");
    }
}
