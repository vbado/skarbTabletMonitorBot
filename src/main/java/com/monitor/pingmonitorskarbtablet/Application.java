package com.monitor.pingmonitorskarbtablet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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

        ZoneId zone = ZoneId.of("Europe/Kiev");

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            LocalDateTime now = LocalDateTime.now(zone);

            for (Map.Entry<String, Instant> entry : PingController.getSessions().entrySet()) {
                String tabletId = entry.getKey();
                LocalDateTime lastPingTime = LocalDateTime.ofInstant(entry.getValue(), zone);

                // якщо останній пінг був більше ніж 600 секунд тому
                if (now.minusSeconds(600).isAfter(lastPingTime)) {
                    Instant lastSent = lastAlert.get(tabletId);

                    LocalTime nowTime = now.toLocalTime();
                    boolean isNight = nowTime.isAfter(LocalTime.of(22, 0))
                            || nowTime.isBefore(LocalTime.of(8, 0));

                    String formattedTime = TimeFormatter.format(entry.getValue());
                    String text = "⚠️ " + tabletId + " - Сеанс неактивний з " + formattedTime + " !";

                    if (lastSent == null) {
                        TelegramNotifier.sendMessage(text);
                        lastAlert.put(tabletId, Instant.now());
                    } else {
                        // повторні повідомлення тільки вдень, раз на годину
                        LocalDateTime lastSentTime = LocalDateTime.ofInstant(lastSent, zone);
                        if (!isNight && now.minusSeconds(3600).isAfter(lastSentTime)) {
                            TelegramNotifier.sendMessage(text);
                            lastAlert.put(tabletId, Instant.now());
                        }
                    }
                }
            }
        }, 0, 60, TimeUnit.SECONDS);
    }


}
