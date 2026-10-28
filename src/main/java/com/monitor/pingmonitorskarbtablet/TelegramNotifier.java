package com.monitor.pingmonitorskarbtablet;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class TelegramNotifier {
    private static final String TOKEN = "8421539239:AAFS72qrPvW_fn7a0sNh1DK-nYEubm3AQp4";
    private static final String CHAT_ID = "339704950";
    private static final String WEBHOOK_URL = "https://skarbtabletmonitorbot-v3.onrender.com/telegram";

    public static void setWebhook() {
        String json = "{"
                + "\"url\":\"" + WEBHOOK_URL + "\","
                + "\"allowed_updates\":[\"message\",\"callback_query\"]"
                + "}";
        postJson("/setWebhook", json, "Webhook registered: " + WEBHOOK_URL);
    }

    public static void answerCallbackQuery(String callbackQueryId) {
        if (callbackQueryId == null || callbackQueryId.isBlank()) {
            return;
        }
        String json = "{\"callback_query_id\":\"" + callbackQueryId + "\"}";
        postJson("/answerCallbackQuery", json, null);
    }

    public static void sendMessage(String text) {
        try {
            String urlStr = "https://api.telegram.org/bot" + TOKEN + "/sendMessage";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String data = "chat_id=" + CHAT_ID + "&text=" + java.net.URLEncoder.encode(text, StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() != 200) {
                System.out.println("Error: " + conn.getResponseCode() + " " + conn.getResponseMessage());
            } else {
                System.out.println("Message sent successfully! - " + text);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendInactiveAlert(String text, String tabletId) {
        String json = "{"
                + "\"chat_id\":\"" + CHAT_ID + "\","
                + "\"text\":\"" + escapeJson(text) + "\","
                + "\"reply_markup\":{"
                    + "  \"inline_keyboard\":[["
                    + "    {\"text\":\"🗑 Видалити\",\"callback_data\":\"DELETE:" + escapeJson(tabletId) + "\"},"
                    + "    {\"text\":\"📊 Статус\",\"callback_data\":\"STATUS\"},"
                    + "    {\"text\":\"🔄 Перезапуск\",\"callback_data\":\"REFRESH:" + escapeJson(tabletId) + "\"},"
                    + "    {\"text\":\"🌐 Отримати IP\",\"callback_data\":\"IP:" + escapeJson(tabletId) + "\"}"
                    + "  ]]"
                + "}"
                + "}";
        postJson("/sendMessage", json, "Alert sent: " + text);
    }

    public static void sendMenu() {
        String json = "{"
                + "\"chat_id\":\"" + CHAT_ID + "\","
                + "\"text\":\"Оберіть дію:\","
                + "\"reply_markup\":{"
                    + "  \"inline_keyboard\":[["
                    + "    {\"text\":\"📊 Статус\",\"callback_data\":\"STATUS\"},"
                    + "    {\"text\":\"🗑 Видалити\",\"callback_data\":\"DELETE\"},"
                    + "    {\"text\":\"📡 Пінг\",\"callback_data\":\"PING\"}"
                    + "  ],["
                    + "    {\"text\":\"🔄 Перезапуск\",\"callback_data\":\"REFRESH\"},"
                    + "    {\"text\":\"🌐 Отримати IP\",\"callback_data\":\"IP\"}"
                    + "  ]]"
                + "}"
                + "}";
        postJson("/sendMessage", json, "Menu sent successfully!");
    }

    private static void postJson(String method, String json, String okLog) {
        try {
            URL url = new URL("https://api.telegram.org/bot" + TOKEN + method);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() != 200) {
                System.out.println("Telegram " + method + " error: "
                        + conn.getResponseCode() + " " + conn.getResponseMessage());
            } else if (okLog != null) {
                System.out.println(okLog);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

