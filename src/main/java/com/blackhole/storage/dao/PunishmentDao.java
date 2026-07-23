package com.blackhole.storage.dao;

import com.blackhole.storage.Database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PunishmentDao {

    private final Database database;
    private final Logger logger;

    public PunishmentDao(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    /** reason is the internal staff-facing motive (section 10) - never shown to the punished player. */
    public void insert(UUID uuid, String type, String reason, long timestampMillis, long durationSeconds) {
        database.getExecutor().submit(() -> {
            String sql = "INSERT INTO punishments (uuid, type, reason, timestamp, duration_seconds) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, type);
                statement.setString(3, reason);
                statement.setLong(4, timestampMillis);
                statement.setLong(5, durationSeconds);
                statement.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error guardando punishment tipo " + type, e);
            }
        });
    }
}
