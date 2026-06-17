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

        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
            scheduler.scheduleAtFixedRate(() -> {
                for (Map.Entry<String, Instant> entry : PingController.getSessions().entrySet()) {
                    String tabletId = entry.getKey();
                    Instant lastPing = entry.getValue();

                    // якщо пінг старший за 2.5 хвилини
                    if (Instant.now().minusSeconds(150).isAfter(lastPing)) {
                        Instant lastSent = lastAlert.get(tabletId);

                        LocalTime nowTime = LocalTime.now();
                        boolean isNight = nowTime.isAfter(LocalTime.of(22, 0)) || nowTime.isBefore(LocalTime.of(8, 0));

                        // якщо повідомлення ще не було
                        String formattedTime = TimeFormatter.format(lastPing);
                        if (lastSent == null) {
                            TelegramNotifier.sendMessage("⚠️ " + tabletId +
                                    " - Сеанс неактивний з " + formattedTime + " !");
                            lastAlert.put(tabletId, Instant.now());
                        } else {
                            // вдень повторюємо раз на годину
                            if (!isNight && Instant.now().minusSeconds(3600).isAfter(lastSent)) {
                                TelegramNotifier.sendMessage("⚠️ " + tabletId +
                                        " - Сеанс неактивний з " + formattedTime + " !");
                                lastAlert.put(tabletId, Instant.now());
                            }
                            // вночі повторів немає
                        }
                    }
                }
            }, 0, 60, TimeUnit.SECONDS);
        }
    }

}
