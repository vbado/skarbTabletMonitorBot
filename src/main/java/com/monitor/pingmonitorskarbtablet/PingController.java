package com.monitor.pingmonitorskarbtablet;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class PingController {
    private static final ConcurrentHashMap<String, Instant> sessions = new ConcurrentHashMap<>();

    @GetMapping("/ping") //http://localhost:8989/ping?id=A35 https://skarbtabletmonitorbot-v3.onrender.com/ping?id=test
    public String ping(@RequestParam String id) {
        sessions.put(id, Instant.now());
        return "OK: " + id;
    }

    @GetMapping("/delete") //https://skarbtabletmonitorbot-v3.onrender.com/delete?id=test
    public String delete(@RequestParam String id) {
        Instant removed = sessions.remove(id);
        Application.clearLastAlert(id);
        if (removed != null) {
            return "Deleted: " + id;
        } else {
            return "Not found: " + id;
        }
    }

    @GetMapping("/status")
    public Map<String, String> status() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Instant> entry : sessions.entrySet()) {
            result.put(entry.getKey(), TimeFormatter.format(entry.getValue()));
        }
        return result;
    }

    public static ConcurrentHashMap<String, Instant> getSessions() {
        return sessions;
    }

    public static String formatStatus() {
        if (sessions.isEmpty()) {
            return "Немає активних сеансів";
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (Map.Entry<String, Instant> entry : sessions.entrySet()) {
            joiner.add(entry.getKey() + ": " + TimeFormatter.format(entry.getValue()));
        }
        return joiner.toString();
    }
}