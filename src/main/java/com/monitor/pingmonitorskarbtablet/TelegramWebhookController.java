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
        }
        expectedAction = null;
    }

    private void deleteSession(String id) {
        Instant removed = PingController.getSessions().remove(id);
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

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asString();
        return value == null || value.isBlank() ? null : value;
    }
}
