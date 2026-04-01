package io.aevum.modules;

import io.aevum.compiler.Module;
import io.aevum.core.BuiltinFunction;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class TimeModule implements Module {

    @Override
    public Map<String, BuiltinFunction> getFunctions() {
        Map<String, BuiltinFunction> map = new HashMap<>();

        map.put("nowMillis", args -> {
            return System.currentTimeMillis();
        });

        map.put("nowNanos", args -> {
            return System.nanoTime();
        });

        map.put("nowSeconds", args -> {
            return System.currentTimeMillis() / 1000;
        });

        map.put("sleep", args -> {
            long millis = ((Number) args.get(0)).longValue();
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });

        map.put("currentDate", args -> {
            return LocalDate.now().toString();
        });

        map.put("currentTime", args -> {
            return LocalTime.now().toString();
        });

        map.put("currentDateTime", args -> {
            return LocalDateTime.now().toString();
        });

        map.put("year", args -> {
            return LocalDate.now().getYear();
        });

        map.put("month", args -> {
            return LocalDate.now().getMonthValue();
        });

        map.put("day", args -> {
            return LocalDate.now().getDayOfMonth();
        });

        map.put("hour", args -> {
            return LocalTime.now().getHour();
        });

        map.put("minute", args -> {
            return LocalTime.now().getMinute();
        });

        map.put("second", args -> {
            return LocalTime.now().getSecond();
        });

        map.put("formatDate", args -> {
            String pattern = (String) args.get(0);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDate.now().format(formatter);
        });

        map.put("formatDateTime", args -> {
            String pattern = (String) args.get(0);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDateTime.now().format(formatter);
        });

        map.put("parseDate", args -> {
            String dateStr = (String) args.get(0);
            String pattern = (String) args.get(1);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            return LocalDate.parse(dateStr, formatter).toString();
        });

        map.put("measureTime", args -> {
            Runnable runnable = (Runnable) args.get(0);
            long start = System.nanoTime();
            runnable.run();
            long end = System.nanoTime();
            return (end - start) / 1_000_000.0;
        });

        return map;
    }

    @Override
    public Map<String, Object> getConstants() {
        return Map.of();
    }
}