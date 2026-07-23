package com.blackhole.storage.dao;

import com.blackhole.storage.Database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerDao {

    private final Database database;
    private final Logger logger;

    public PlayerDao(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public void upsert(UUID uuid, String name, long lastSeenMillis) {
        database.getExecutor().submit(() -> {
            String sql = "INSERT INTO players (uuid, name, last_seen) VALUES (?, ?, ?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, last_seen = excluded.last_seen";
            try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setLong(3, lastSeenMillis);
                statement.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error guardando jugador " + name, e);
            }
        });
    }
}
