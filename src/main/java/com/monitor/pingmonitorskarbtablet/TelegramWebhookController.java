package com.monitor.pingmonitorskarbtablet;

import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@RestController
public class TelegramWebhookController {

    // простий стан: що очікує бот від користувача
    private String expectedAction = null;

    @PostMapping("/telegram")
    public void handleUpdate(@RequestBody String updateJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(updateJson);

            // якщо натиснули кнопку
            if (root.has("callback_query")) {
                JsonNode callback = root.get("callback_query");
                String data = callback.get("data").asText();
                String chatId = callback.get("message").get("chat").get("id").asText();

                if ("STATUS".equals(data)) {
                    String status = callApi("https://skarbtabletmonitorbot-v3.onrender.com/status");
                    TelegramNotifier.sendMessage("📊 Статус:\n" + status);
                    expectedAction = null; // нічого не очікуємо

                } else if ("DELETE".equals(data)) {
                    TelegramNotifier.sendMessage("🗑 Введи ID для видалення...");
                    expectedAction = "DELETE";

                } else if ("PING".equals(data)) {
                    TelegramNotifier.sendMessage("📡 Введи ID для пінгу...");
                    expectedAction = "PING";
                }
            }

            // якщо користувач ввів текст
            if (root.has("message")) {
                JsonNode msg = root.get("message");
                String text = msg.get("text").asText();
                String chatId = msg.get("chat").get("id").asText();

                if ("DELETE".equals(expectedAction)) {
                    String result = callApi("https://skarbtabletmonitorbot-v3.onrender.com/delete?id=" + text);
                    TelegramNotifier.sendMessage("Результат видалення: " + result);
                    expectedAction = null;

                } else if ("PING".equals(expectedAction)) {
                    String result = callApi("https://skarbtabletmonitorbot-v3.onrender.com/ping?id=" + text);
                    TelegramNotifier.sendMessage("Результат пінгу: " + result);
                    expectedAction = null;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String callApi(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");

            BufferedReader in;
            if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            } else {
                in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            }

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();
            return response.toString();
        } catch (Exception e) {
            return "Помилка: " + e.getMessage();
        }
    }

}

