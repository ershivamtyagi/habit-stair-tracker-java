package com.stairprogress;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CsvActivityRepository {
    private static final String HEADER = "date,activity,completedAt";
    private final Path filePath;

    public CsvActivityRepository(Path filePath) {
        this.filePath = filePath;
    }

    public List<ActivityLog> loadAll() {
        ensureFileExists();

        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            List<ActivityLog> logs = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = parseCsvLine(line);
                if (parts.length != 3) {
                    continue;
                }
                logs.add(new ActivityLog(
                        LocalDate.parse(parts[0]),
                        parts[1],
                        LocalDateTime.parse(parts[2])
                ));
            }
            logs.sort(Comparator.comparing(ActivityLog::date).reversed());
            return logs;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read activity log CSV.", exception);
        }
    }

    public boolean hasEntryFor(LocalDate date) {
        return loadAll().stream().anyMatch(log -> log.date().equals(date));
    }

    public ActivityLog saveOrUpdate(LocalDate date, String activityName) {
        ensureFileExists();

        List<ActivityLog> logs = new ArrayList<>(loadAll());
        ActivityLog newLog = new ActivityLog(date, activityName.strip(), LocalDateTime.now());
        logs.removeIf(log -> log.date().equals(date));
        logs.add(newLog);
        writeAll(logs);
        return newLog;
    }

    public boolean deleteByDate(LocalDate date) {
        ensureFileExists();
        List<ActivityLog> logs = new ArrayList<>(loadAll());
        boolean removed = logs.removeIf(log -> log.date().equals(date));
        if (removed) {
            writeAll(logs);
        }
        return removed;
    }

    private void writeAll(List<ActivityLog> logs) {
        logs.sort(Comparator.comparing(ActivityLog::date));
        StringBuilder builder = new StringBuilder(HEADER).append(System.lineSeparator());
        for (ActivityLog log : logs) {
            builder.append(toCsvLine(log)).append(System.lineSeparator());
        }

        try {
            Files.writeString(
                    filePath,
                    builder.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write activity log CSV.", exception);
        }
    }

    private void ensureFileExists() {
        try {
            if (Files.notExists(filePath.getParent())) {
                Files.createDirectories(filePath.getParent());
            }
            if (Files.notExists(filePath)) {
                Files.writeString(filePath, HEADER + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to initialize CSV storage.", exception);
        }
    }

    private String toCsvLine(ActivityLog log) {
        return escape(log.date().toString()) + ","
                + escape(log.activityName()) + ","
                + escape(log.completedAt().toString());
    }

    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        values.add(current.toString());
        return values.toArray(String[]::new);
    }

    private String escape(String value) {
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
