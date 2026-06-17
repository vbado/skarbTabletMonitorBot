package com.monitor.pingmonitorskarbtablet;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TelegramNotifier {
    private static final String TOKEN = "8421539239:AAFS72qrPvW_fn7a0sNh1DK-nYEubm3AQp4";
    private static final String CHAT_ID = "339704950";

    public static void sendMessage(String text) {
        try {
            String urlStr = "https://api.telegram.org/bot" + TOKEN + "/sendMessage";
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String data = "chat_id=" + CHAT_ID + "&text=" + java.net.URLEncoder.encode(text, "UTF-8");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data.getBytes());
            }

            if (conn.getResponseCode() != 200) {
                System.out.println("Error: " + conn.getResponseCode() + " " + conn.getResponseMessage());
            } else {
                System.out.println("Message sent successfully!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

