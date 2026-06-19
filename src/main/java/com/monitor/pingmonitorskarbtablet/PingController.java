package com.monitor.pingmonitorskarbtablet;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class PingController {
    private static final ConcurrentHashMap<String, Instant> sessions = new ConcurrentHashMap<>();

    @GetMapping("/ping") //http://localhost:8989/ping?id=A35 https://skarbtabletmonitorbot-v3.onrender.com/ping?id=test
    public String ping(@RequestParam String id) {
        sessions.put(id, Instant.now());
        return "OK: " + id;
    }

    public static ConcurrentHashMap<String, Instant> getSessions() {
        return sessions;
    }
}