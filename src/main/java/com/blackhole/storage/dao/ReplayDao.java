package com.blackhole.storage.dao;

import com.blackhole.storage.Database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ReplayDao {

    private final Database database;
    private final Logger logger;

    public ReplayDao(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public static final class Row {
        public final int tick;
        public final double x, y, z;
        public final float yaw, pitch;
        public final boolean onGround;

        public Row(int tick, double x, double y, double z, float yaw, float pitch, boolean onGround) {
            this.tick = tick;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.onGround = onGround;
        }
    }

    public void insertSnapshots(long violationId, List<Row> rows) {
        if (rows.isEmpty()) {
            return;
        }
        database.getExecutor().submit(() -> {
            String sql = "INSERT INTO replay_snapshots (violation_id, tick, x, y, z, yaw, pitch, on_ground) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
                for (Row row : rows) {
                    statement.setLong(1, violationId);
                    statement.setInt(2, row.tick);
                    statement.setDouble(3, row.x);
                    statement.setDouble(4, row.y);
                    statement.setDouble(5, row.z);
                    statement.setFloat(6, row.yaw);
                    statement.setFloat(7, row.pitch);
                    statement.setBoolean(8, row.onGround);
                    statement.addBatch();
                }
                statement.executeBatch();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error guardando replay_snapshots para violation " + violationId, e);
            }
        });
    }
}
