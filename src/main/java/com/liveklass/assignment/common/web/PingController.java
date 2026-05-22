package com.liveklass.assignment.common.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @GetMapping("/api/ping")
    public Map<String, Object> ping(@RequestHeader("X-User-Id") Long userId) {
        return Map.of("userId", userId);
    }
}
