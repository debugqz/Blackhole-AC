package com.blackhole.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Single SQLite connection + single-threaded executor (section 10): every DAO
 * call runs through this executor so writes are serialized (SQLite doesn't
 * like concurrent writers) while never blocking the main thread or the
 * packet/Netty thread that requested the write.
 */
public final class Database {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Blackhole-DB");
        thread.setDaemon(true);
        return thread;
    });

    private Connection connection;

    public void connect(File dataFolder) throws SQLException {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File dbFile = new File(dataFolder, "anticheat.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
        }
        migrate();
    }

    private void migrate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS players (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "last_seen INTEGER NOT NULL)");

            statement.execute("CREATE TABLE IF NOT EXISTS violations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "uuid TEXT NOT NULL, " +
                    "check_name TEXT NOT NULL, " +
                    "vl_at_time REAL NOT NULL, " +
                    "timestamp INTEGER NOT NULL, " +
                    "evidence TEXT)");

            statement.execute("CREATE TABLE IF NOT EXISTS punishments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "uuid TEXT NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "reason TEXT NOT NULL, " +
                    "timestamp INTEGER NOT NULL, " +
                    "duration_seconds INTEGER NOT NULL)");

            statement.execute("CREATE TABLE IF NOT EXISTS xray_stats (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "blocks_mined INTEGER NOT NULL, " +
                    "ores_found INTEGER NOT NULL, " +
                    "ratio REAL NOT NULL, " +
                    "blind_reveals INTEGER NOT NULL)");

            statement.execute("CREATE TABLE IF NOT EXISTS replay_snapshots (" +
                    "violation_id INTEGER NOT NULL, " +
                    "tick INTEGER NOT NULL, " +
                    "x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, " +
                    "yaw REAL NOT NULL, pitch REAL NOT NULL, " +
                    "on_ground INTEGER NOT NULL)");

            statement.execute("CREATE INDEX IF NOT EXISTS idx_violations_uuid ON violations(uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_punishments_uuid ON punishments(uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_replay_violation_id ON replay_snapshots(violation_id)");
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ignored) {
            // best-effort close on shutdown
        }
    }
}
