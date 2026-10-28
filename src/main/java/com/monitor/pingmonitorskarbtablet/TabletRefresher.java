package com.monitor.pingmonitorskarbtablet;

import java.net.HttpURLConnection;
import java.net.URL;

final class TabletRefresher {
    private static final int PORT = 1980;

    private TabletRefresher() {
    }

    static boolean refresh(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            System.out.println("Refresh skipped: tablet IP is unknown");
            return false;
        }
        String url = "http://" + ip.trim() + ":" + PORT + "/refresh";
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            System.out.println("Refresh " + url + " -> " + code);
            return code == 200;
        } catch (Exception e) {
            System.out.println("Refresh " + url + " failed: " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
