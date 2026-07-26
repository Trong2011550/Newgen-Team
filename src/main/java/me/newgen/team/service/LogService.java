package me.newgen.team.service;

import me.newgen.team.config.ConfigManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Categorised file logging: plugins/NewGenTeam/logs/<category>/<yyyy-MM-dd>.log
 * Categories: daily, errors, transactions, admin (toggled per-category in logs.yml).
 * Writes happen on a single background thread so the main thread never blocks on IO.
 */
public final class LogService {

    public static final String DAILY = "daily";
    public static final String ERRORS = "errors";
    public static final String TRANSACTIONS = "transactions";
    public static final String ADMIN = "admin";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Logger console;
    private final ConfigManager config;
    private final File baseDir;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NewGenTeam-LogWriter");
        t.setDaemon(true);
        return t;
    });

    public LogService(Logger console, ConfigManager config, File dataFolder) {
        this.console = console;
        this.config = config;
        this.baseDir = new File(dataFolder, "logs");
    }

    /** General daily activity (team create/disband, join/leave...). */
    public void daily(String message) { write(DAILY, message); }

    /** Errors (storage failures, hook failures...). Also mirrored to console. */
    public void error(String message) {
        console.warning(message);
        write(ERRORS, message);
    }

    /** Team bank transactions (deposit/withdraw). */
    public void transaction(String message) { write(TRANSACTIONS, message); }

    /** Admin actions (/team admin, /team reload...). */
    public void admin(String message) { write(ADMIN, message); }

    /** Debug diagnostics: console + daily log, only when debug: true in logs.yml. */
    public void debug(String message) {
        if (!config.debug()) return;
        console.info("[DEBUG] " + message);
        write(DAILY, "[DEBUG] " + message);
    }

    private void write(String category, String message) {
        if (!config.logEnabled(category)) return;
        String line = "[" + LocalTime.now().format(TIME) + "] " + message + System.lineSeparator();
        String day = LocalDate.now().format(DAY);
        writer.execute(() -> {
            try {
                File dir = new File(baseDir, category);
                if (!dir.exists() && !dir.mkdirs()) return;
                Path file = new File(dir, day + ".log").toPath();
                Files.writeString(file, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                console.warning("Failed to write " + category + " log: " + ex.getMessage());
            }
        });
    }

    /** Flushes pending writes and stops the writer thread. Call on plugin disable. */
    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) writer.shutdownNow();
        } catch (InterruptedException ex) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
