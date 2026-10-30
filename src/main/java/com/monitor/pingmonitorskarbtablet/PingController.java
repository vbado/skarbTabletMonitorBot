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
    private static final ConcurrentHashMap<String, String> internalIps = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> externalIps = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Instant> pendingRefresh = new ConcurrentHashMap<>();

    @GetMapping(value = "/ping", produces = MediaType.TEXT_PLAIN_VALUE)
    // 1С: /ping?id=A35&ip=176.x   оболонка: /ping?externalIp=176.x&internalIp=192.168.x
    public ResponseEntity<String> ping(@RequestParam(required = false) String id,
                                       @RequestParam(required = false) String externalIp,
                                       @RequestParam(required = false) String internalIp,
                                       @RequestParam(required = false) String ip) {
        String safeExternal = ipv4OrNull(first(externalIp, ip));
        String safeInternal = ipv4OrNull(internalIp);
        String resolvedId = blankToNull(id);
        if (resolvedId == null && safeExternal != null) {
            resolvedId = findUniqueIdByExternalIp(safeExternal);
        }
        if (resolvedId == null) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("OK");
        }
        sessions.put(resolvedId, Instant.now());
        if (safeExternal != null) {
            externalIps.put(resolvedId, safeExternal);
        }
        if (safeInternal != null) {
            internalIps.put(resolvedId, safeInternal);
        }
        if (pendingRefresh.remove(resolvedId) != null) {
            TelegramNotifier.sendMessage("🔄 " + resolvedId + " виконує перезапуск");
            return ResponseEntity.ok()
                    .header("X-Tablet-Id", resolvedId)
                    .header("X-Tablet-Refresh", "1")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("REFRESH");
        }
        StringBuilder body = new StringBuilder("OK: ").append(resolvedId);
        if (safeExternal != null) {
            body.append(" externalIp=").append(safeExternal);
        }
        if (safeInternal != null) {
            body.append(" internalIp=").append(safeInternal);
        }
        return ResponseEntity.ok()
                .header("X-Tablet-Id", resolvedId)
                .contentType(MediaType.TEXT_PLAIN)
                .body(body.toString());
    }

    @GetMapping("/delete") //https://skarbtabletmonitorbot-v3.onrender.com/delete?id=test
    public String delete(@RequestParam String id) {
        Instant removed = sessions.remove(id);
        internalIps.remove(id);
        externalIps.remove(id);
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

    @GetMapping("/ip") //https://skarbtabletmonitorbot-v3.onrender.com/ip?id=A35
    public Map<String, String> ip(@RequestParam String id) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("externalIp", orUnknown(externalIps.get(id)));
        result.put("internalIp", orUnknown(internalIps.get(id)));
        Instant seen = sessions.get(id);
        if (seen != null) {
            result.put("lastSeen", TimeFormatter.format(seen));
        }
        return result;
    }

    @GetMapping("/status")
    public Map<String, Map<String, String>> status() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Instant> entry : sessions.entrySet()) {
            Map<String, String> info = new LinkedHashMap<>();
            info.put("lastSeen", TimeFormatter.format(entry.getValue()));
            info.put("externalIp", orUnknown(externalIps.get(entry.getKey())));
            info.put("internalIp", orUnknown(internalIps.get(entry.getKey())));
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
        return externalIps.get(id);
    }

    public static String getInternalIp(String id) {
        return internalIps.get(id);
    }

    public static String getExternalIp(String id) {
        return externalIps.get(id);
    }

    public static String formatIps(String id) {
        return id
                + "\nexternalIp: " + orUnknown(externalIps.get(id))
                + "\ninternalIp: " + orUnknown(internalIps.get(id));
    }

    public static Instant forget(String id) {
        internalIps.remove(id);
        externalIps.remove(id);
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
        return TimeFormatter.format(seenAt)
                + ", externalIp " + orUnknown(externalIps.get(id))
                + ", internalIp " + orUnknown(internalIps.get(id));
    }

    private static String findUniqueIdByExternalIp(String externalIp) {
        String found = null;
        for (Map.Entry<String, String> entry : externalIps.entrySet()) {
            if (!externalIp.equals(entry.getValue())) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = entry.getKey();
        }
        return found;
    }

    private static String first(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String orUnknown(String ip) {
        return ip == null ? "невідомий" : ip;
    }

    private static String ipv4OrNull(String ip) {
        if (ip == null) {
            return null;
        }
        String trimmed = ip.trim();
        return IPV4.matcher(trimmed).matches() ? trimmed : null;
    }
}
