package com.typesafe.jevplayer.core.director;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EpisodeEventLogger implements AutoCloseable {
    private static final Gson GSON = new GsonBuilder().create();

    public record EpisodeEvent(
            long timestampMs,
            String type, // "milestone_reached", "night_survived", "death", "decision", "kill_switch"
            String details,
            long elapsedSeconds
    ) {}

    private final Path logFilePath;
    private final BlockingQueue<EpisodeEvent> queue = new LinkedBlockingQueue<>(2048);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread writerThread;

    public EpisodeEventLogger(Path logFilePath) {
        this.logFilePath = logFilePath;
        this.writerThread = Thread.ofPlatform().daemon().name("jev-event-logger").start(this::drainLoop);
    }

    public void logEvent(String type, String details, long elapsedSeconds) {
        queue.offer(new EpisodeEvent(System.currentTimeMillis(), type, details, elapsedSeconds));
    }

    private void drainLoop() {
        File file = logFilePath.toFile();
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            while (running.get() || !queue.isEmpty()) {
                EpisodeEvent event = queue.poll();
                if (event != null) {
                    bw.write(GSON.toJson(event));
                    bw.newLine();
                    bw.flush();
                } else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[JevPlayer] Failed to write episode event log: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        running.set(false);
        writerThread.interrupt();
    }
}
