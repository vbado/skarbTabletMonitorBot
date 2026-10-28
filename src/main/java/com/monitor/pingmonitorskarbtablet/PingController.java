package com.monitor.pingmonitorskarbtablet;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@RestController
public class PingController {
    private static final Pattern IPV4 = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)$");
    private static final ConcurrentHashMap<String, Instant> sessions = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> tabletIps = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Instant> pendingRefresh = new ConcurrentHashMap<>();

    @GetMapping(value = "/ping", produces = MediaType.TEXT_PLAIN_VALUE)
    //https://skarbtabletmonitorbot-v3.onrender.com/ping?id=A35&ip=192.168.0.15
    public ResponseEntity<String> ping(@RequestParam String id,
                                       @RequestParam(required = false) String ip) {
        sessions.put(id, Instant.now());
        String safeIp = ipv4OrNull(ip);
        if (safeIp != null) {
            tabletIps.put(id, safeIp);
        }
        if (pendingRefresh.remove(id) != null) {
            TelegramNotifier.sendMessage("🔄 " + id + " виконує перезапуск");
            return ResponseEntity.ok()
                    .header("X-Tablet-Refresh", "1")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("REFRESH");
        }
        String storedIp = tabletIps.get(id);
        String body = storedIp == null ? "OK: " + id : "OK: " + id + " " + storedIp;
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(body);
    }

    @GetMapping("/delete") //https://skarbtabletmonitorbot-v3.onrender.com/delete?id=test
    public String delete(@RequestParam String id) {
        Instant removed = sessions.remove(id);
        tabletIps.remove(id);
        pendingRefresh.remove(id);
        Application.clearLastAlert(id);
        if (removed != null) {
            return "Deleted: " + id;
        } else {
            return "Not found: " + id;
        }
    }

    @GetMapping("/refresh") //https://skarbtabletmonitorbot-v3.onrender.com/refresh?id=A35
    public String refresh(@RequestParam String id) {
        if (!requestRefresh(id)) {
            return "Not found: " + id;
        }
        return "Queued: " + id;
    }

    @GetMapping("/status")
    public Map<String, Map<String, String>> status() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Instant> entry : sessions.entrySet()) {
            Map<String, String> info = new LinkedHashMap<>();
            info.put("lastSeen", TimeFormatter.format(entry.getValue()));
            String ip = tabletIps.get(entry.getKey());
            info.put("ip", ip != null ? ip : "невідомий");
            result.put(entry.getKey(), info);
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
            joiner.add(entry.getKey() + ": " + describe(entry.getKey(), entry.getValue()));
        }
        return joiner.toString();
    }

    public static String getIp(String id) {
        return tabletIps.get(id);
    }

    public static Instant forget(String id) {
        tabletIps.remove(id);
        pendingRefresh.remove(id);
        return sessions.remove(id);
    }

    public static boolean requestRefresh(String id) {
        if (!sessions.containsKey(id)) {
            return false;
        }
        pendingRefresh.put(id, Instant.now());
        return true;
    }

    private static String describe(String id, Instant seenAt) {
        String when = TimeFormatter.format(seenAt);
        String ip = tabletIps.get(id);
        if (ip == null) {
            return when + ", IP невідомий";
        }
        return when + ", IP " + ip;
    }

    private static String ipv4OrNull(String ip) {
        if (ip == null) {
            return null;
        }
        String trimmed = ip.trim();
        return IPV4.matcher(trimmed).matches() ? trimmed : null;
    }
}