package com.monitor.pingmonitorskarbtablet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class Application {

    private static final ConcurrentHashMap<String, Instant> lastAlert = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            for (Map.Entry<String, Instant> entry : PingController.getSessions().entrySet()) {
                String tabletId = entry.getKey();
                Instant lastPing = entry.getValue();

                if (Instant.now().minusSeconds(150).isAfter(lastPing)) {
                    Instant lastSent = lastAlert.get(tabletId);

                    LocalTime nowTime = LocalTime.now();
                    boolean isNight = nowTime.isAfter(LocalTime.of(22, 0)) || nowTime.isBefore(LocalTime.of(8, 0));

                    String formattedTime = TimeFormatter.format(lastPing);
                    String text = "⚠️ " + tabletId + " - Сеанс неактивний вже з " + formattedTime + " !";

                    if (lastSent == null) {
                        TelegramNotifier.sendMessage(text);
                        lastAlert.put(tabletId, Instant.now());
                    } else {
                        if (!isNight && Instant.now().minusSeconds(3600).isAfter(lastSent)) {
                            TelegramNotifier.sendMessage(text);
                            lastAlert.put(tabletId, Instant.now());
                        }
                    }
                }
            }
        }, 0, 60, TimeUnit.SECONDS);
    }


}
