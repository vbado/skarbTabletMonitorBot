package com.monitor.pingmonitorskarbtablet;

import jakarta.servlet.http.HttpServletRequest;
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
    private static final ConcurrentHashMap<String, String> apps = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Instant> pendingRefresh = new ConcurrentHashMap<>();

    @GetMapping(value = "/ping", produces = MediaType.TEXT_PLAIN_VALUE)
    // стара: /ping?id=A35&ip=...   нова: /ping?id=A35&internalIp=192.168.0.15&app=diamond
    public ResponseEntity<String> ping(@RequestParam String id,
                                       @RequestParam(required = false) String ip,
                                       @RequestParam(required = false) String externalIp,
                                       @RequestParam(required = false) String internalIp,
                                       @RequestParam(required = false) String app,
                                       HttpServletRequest request) {
        sessions.put(id, Instant.now());
        storeClient(id, app, ip, internalIp);
        storeIps(id, ip, externalIp, internalIp, request);
        if (pendingRefresh.remove(id) != null) {
            TelegramNotifier.sendMessage("🔄 " + id + " виконує перезапуск");
            return ResponseEntity.ok()
                    .header("X-Tablet-Refresh", "1")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("REFRESH");
        }
        String storedExternal = externalIps.get(id);
        String body = storedExternal == null ? "OK: " + id : "OK: " + id + " " + storedExternal;
        String storedInternal = internalIps.get(id);
        if (storedInternal != null) {
            body += " internalIp=" + storedInternal;
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(body);
    }

    @GetMapping("/delete") //https://skarbtabletmonitorbot-v3.onrender.com/delete?id=test
    public String delete(@RequestParam String id) {
        Instant removed = sessions.remove(id);
        internalIps.remove(id);
        externalIps.remove(id);
        apps.remove(id);
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
        result.put("app", orUnknown(apps.get(id)));
        result.put("ip", orUnknown(externalIps.get(id)));
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
            info.put("app", orUnknown(apps.get(entry.getKey())));
            info.put("ip", orUnknown(externalIps.get(entry.getKey())));
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
                + "\napp: " + orUnknown(apps.get(id))
                + "\nexternalIp: " + orUnknown(externalIps.get(id))
                + "\ninternalIp: " + orUnknown(internalIps.get(id));
    }

    public static Instant forget(String id) {
        internalIps.remove(id);
        externalIps.remove(id);
        apps.remove(id);
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
                + " [" + orUnknown(apps.get(id)) + "]"
                + ", externalIp " + orUnknown(externalIps.get(id))
                + ", internalIp " + orUnknown(internalIps.get(id));
    }

    private static void storeClient(String id, String app, String ip, String internalIp) {
        if (app != null && !app.isBlank()) {
            apps.put(id, app.trim());
        } else if (internalIp != null && !internalIp.isBlank()) {
            apps.putIfAbsent(id, "diamond");
        } else if (ipv4OrNull(ip) != null && isPrivate(ip.trim())) {
            apps.putIfAbsent(id, "shell");
        } else if (ipv4OrNull(ip) != null) {
            apps.putIfAbsent(id, "1c");
        }
    }

    private static void storeIps(String id, String ip, String externalIp, String internalIp,
                                 HttpServletRequest request) {
        String fromInternal = ipv4OrNull(internalIp);
        if (fromInternal != null) {
            internalIps.put(id, fromInternal);
        }
        String fromExternal = ipv4OrNull(externalIp);
        if (fromExternal != null) {
            externalIps.put(id, fromExternal);
        }
        String fromIp = ipv4OrNull(ip);
        if (fromIp != null) {
            if (isPrivate(fromIp)) {
                internalIps.putIfAbsent(id, fromIp);
            } else {
                externalIps.put(id, fromIp);
            }
        }
        if (externalIps.get(id) == null) {
            String wan = ipv4OrNull(clientIp(request));
            if (wan != null && !isPrivate(wan)) {
                externalIps.put(id, wan);
            }
        }
    }

    private static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static boolean isPrivate(String ip) {
        String[] p = ip.split("\\.");
        if (p.length != 4) {
            return false;
        }
        int a = Integer.parseInt(p[0]);
        int b = Integer.parseInt(p[1]);
        return a == 10
                || a == 127
                || (a == 192 && b == 168)
                || (a == 172 && b >= 16 && b <= 31);
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