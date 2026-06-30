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

    @PostMapping("/telegram")
    public void handleUpdate(@RequestBody String updateJson) {

       // System.out.println("updateJson " + updateJson);

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(updateJson);

            // перевіряємо чи є callback_query
            if (root.has("callback_query")) {
                JsonNode callback = root.get("callback_query");
                String data = callback.get("data").asText();
                String chatId = callback.get("message").get("chat").get("id").asText();

                //System.out.println("data " + data);

                if ("STATUS".equals(data)) {
                    // виклик твого /status

                    String status = callApi("https://skarbtabletmonitorbot-v3.onrender.com/status");
                    TelegramNotifier.sendMessage("📊 Статус:\n" + status);

                    //System.out.println("STATUS " + status);


                } else if ("DELETE".equals(data)) {
                    TelegramNotifier.sendMessage("🗑 Введи ID для видалення...");
                    // тут можна зробити виклик /delete?id=...
                    String status = callApi("https://skarbtabletmonitorbot-v3.onrender.com/delete?id=");
                    TelegramNotifier.sendMessage(status);

                } else if ("PING".equals(data)) {
                    TelegramNotifier.sendMessage("📡 Введи ID для пінгу...");
                    // тут можна зробити виклик /ping?id=...
                    String status = callApi("https://skarbtabletmonitorbot-v3.onrender.com/ping?id=");
                    TelegramNotifier.sendMessage(status);
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
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
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
