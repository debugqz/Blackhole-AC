package com.blackhole.storage.dao;

import com.blackhole.storage.Database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class XrayStatsDao {

    private final Database database;
    private final Logger logger;

    public XrayStatsDao(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public void upsert(UUID uuid, int blocksMined, int oresFound, double ratio, int blindReveals) {
        database.getExecutor().submit(() -> {
            String sql = "INSERT INTO xray_stats (uuid, blocks_mined, ores_found, ratio, blind_reveals) VALUES (?, ?, ?, ?, ?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET blocks_mined = excluded.blocks_mined, "
                    + "ores_found = excluded.ores_found, ratio = excluded.ratio, blind_reveals = excluded.blind_reveals";
            try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setInt(2, blocksMined);
                statement.setInt(3, oresFound);
                statement.setDouble(4, ratio);
                statement.setInt(5, blindReveals);
                statement.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error guardando xray_stats", e);
            }
        });
    }
}
