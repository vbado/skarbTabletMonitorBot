package com.monitor.pingmonitorskarbtablet;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@RestController
public class TelegramWebhookController {

    private volatile String expectedAction = null;

    @PostMapping("/telegram")
    public void handleUpdate(@RequestBody String updateJson) {
        try {
            JsonMapper mapper = JsonMapper.builder().build();
            JsonNode root = mapper.readTree(updateJson);

            if (root.has("callback_query")) {
                handleCallback(root.get("callback_query"));
            }

            if (root.has("message")) {
                handleMessage(root.get("message"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleCallback(JsonNode callback) {
        String data = text(callback, "data");
        String callbackId = text(callback, "id");
        TelegramNotifier.answerCallbackQuery(callbackId);

        if (data == null) {
            return;
        }

        if ("STATUS".equals(data)) {
            TelegramNotifier.sendMessage("📊 Статус:\n" + PingController.formatStatus());
            expectedAction = null;
        } else if (data.startsWith("DELETE:")) {
            deleteSession(data.substring("DELETE:".length()));
            expectedAction = null;
        } else if ("DELETE".equals(data)) {
            TelegramNotifier.sendMessage("🗑 Введи ID для видалення...");
            expectedAction = "DELETE";
        } else if (data.startsWith("PING:")) {
            pingSession(data.substring("PING:".length()));
            expectedAction = null;
        } else if ("PING".equals(data)) {
            TelegramNotifier.sendMessage("📡 Введи ID для пінгу...");
            expectedAction = "PING";
        } else if (data.startsWith("REFRESH:")) {
            expectedAction = null;
            String id = data.substring("REFRESH:".length());
            new Thread(() -> refreshSession(id), "tablet-refresh").start();
        } else if ("REFRESH".equals(data)) {
            TelegramNotifier.sendMessage("🔄 Введи ID для перезапуску...");
            expectedAction = "REFRESH";
        }
    }

    private void handleMessage(JsonNode msg) {
        String text = text(msg, "text");
        if (text == null || expectedAction == null) {
            return;
        }

        if ("DELETE".equals(expectedAction)) {
            deleteSession(text.trim());
        } else if ("PING".equals(expectedAction)) {
            pingSession(text.trim());
        } else if ("REFRESH".equals(expectedAction)) {
            new Thread(() -> refreshSession(text.trim()), "tablet-refresh").start();
        }
        expectedAction = null;
    }

    private void deleteSession(String id) {
        Instant removed = PingController.forget(id);
        Application.clearLastAlert(id);
        if (removed != null) {
            TelegramNotifier.sendMessage("Видалено: " + id);
        } else {
            TelegramNotifier.sendMessage("Не знайдено: " + id);
        }
    }

    private void pingSession(String id) {
        PingController.getSessions().put(id, Instant.now());
        Application.clearLastAlert(id);
        TelegramNotifier.sendMessage("OK: " + id);
    }

    private void refreshSession(String id) {
        String ip = PingController.getIp(id);
        if (ip == null) {
            TelegramNotifier.sendMessage("Немає IP для " + id
                    + ". Планшет має пінгувати з &ip=");
            return;
        }
        boolean ok = TabletRefresher.refresh(ip);
        if (ok) {
            TelegramNotifier.sendMessage("🔄 Перезапуск " + id + " (" + ip + "): OK");
        } else {
            TelegramNotifier.sendMessage("🔄 Перезапуск " + id + " (" + ip + "): не вдалось");
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asString();
        return value == null || value.isBlank() ? null : value;
    }
}
