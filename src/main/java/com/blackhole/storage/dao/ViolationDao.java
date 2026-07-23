package com.blackhole.storage.dao;

import com.blackhole.storage.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ViolationDao {

    public static final class Row {
        public final String checkName;
        public final double vlAtTime;
        public final long timestampMillis;

        public Row(String checkName, double vlAtTime, long timestampMillis) {
            this.checkName = checkName;
            this.vlAtTime = vlAtTime;
            this.timestampMillis = timestampMillis;
        }
    }

    private final Database database;
    private final Logger logger;

    public ViolationDao(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    /** Returns the generated violation id (or -1 on failure) so ReplayDao can attach evidence to it. */
    public CompletableFuture<Long> insert(UUID uuid, String checkName, double vlAtTime, long timestampMillis, String evidenceJson) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        database.getExecutor().submit(() -> {
            String sql = "INSERT INTO violations (uuid, check_name, vl_at_time, timestamp, evidence) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement statement = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, checkName);
                statement.setDouble(3, vlAtTime);
                statement.setLong(4, timestampMillis);
                statement.setString(5, evidenceJson);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    future.complete(keys.next() ? keys.getLong(1) : -1L);
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error guardando violation de " + checkName, e);
                future.complete(-1L);
            }
        });
        return future;
    }

    public CompletableFuture<List<Row>> findRecent(UUID uuid, int limit) {
        CompletableFuture<List<Row>> future = new CompletableFuture<>();
        database.getExecutor().submit(() -> {
            String sql = "SELECT check_name, vl_at_time, timestamp FROM violations "
                    + "WHERE uuid = ? ORDER BY timestamp DESC LIMIT ?";
            List<Row> rows = new ArrayList<>();
            try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setInt(2, limit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        rows.add(new Row(resultSet.getString("check_name"), resultSet.getDouble("vl_at_time"),
                                resultSet.getLong("timestamp")));
                    }
                }
                future.complete(rows);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error leyendo historial de violaciones", e);
                future.complete(rows);
            }
        });
        return future;
    }
}
