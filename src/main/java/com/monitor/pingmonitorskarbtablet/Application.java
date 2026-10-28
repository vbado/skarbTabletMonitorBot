package com.monitor.pingmonitorskarbtablet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.Instant;
import java.time.LocalDate;
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
    private static volatile LocalDate lastDailyStatusDate = null;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);

        TelegramNotifier.setWebhook();
        TelegramNotifier.sendMenu();

        ZoneId zone = ZoneId.of("Europe/Kiev");

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        // Telegram знімає вебхук, якщо хтось викликає getUpdates з цим токеном
        scheduler.scheduleAtFixedRate(TelegramNotifier::setWebhook, 10, 10, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(() -> {
            LocalDateTime now = LocalDateTime.now(zone);

            sendDailyStatusIfNeeded(now);

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
                        notifyInactive(tabletId, text);
                    } else {
                        // повторні повідомлення тільки вдень, раз на годину
                        LocalDateTime lastSentTime = LocalDateTime.ofInstant(lastSent, zone);
                        if (!isNight && now.minusSeconds(3600).isAfter(lastSentTime)) {
                            notifyInactive(tabletId, text);
                        }
                    }
                }
            }
        }, 0, 60, TimeUnit.SECONDS);
    }

    public static void clearLastAlert(String tabletId) {
        lastAlert.remove(tabletId);
    }

    private static void notifyInactive(String tabletId, String text) {
        TabletRefresher.refresh(PingController.getIp(tabletId));
        TelegramNotifier.sendInactiveAlert(text, tabletId);
        lastAlert.put(tabletId, Instant.now());
    }

    private static void sendDailyStatusIfNeeded(LocalDateTime now) {
        if (now.toLocalTime().isBefore(LocalTime.of(8, 0))) {
            return;
        }
        LocalDate today = now.toLocalDate();
        if (today.equals(lastDailyStatusDate)) {
            return;
        }
        lastDailyStatusDate = today;
        TelegramNotifier.sendMessage("📊 Ранковий статус:\n" + PingController.formatStatus());
    }
}
