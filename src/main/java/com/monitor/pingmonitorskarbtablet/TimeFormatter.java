package com.monitor.pingmonitorskarbtablet;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TimeFormatter {

    private static final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm", new Locale("uk"))
                    .withZone(ZoneId.of("Europe/Kiev"));

    public static String format(Instant instant) {
        return formatter.format(LocalDateTime.ofInstant(instant, ZoneId.of("Europe/Kiev")));
    }
}
