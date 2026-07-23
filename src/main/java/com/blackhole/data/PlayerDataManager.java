package com.blackhole.data;

import com.blackhole.physics.PhysicsState;
import com.blackhole.util.Vector3d;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDataManager {

    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();

    public PlayerData getOrCreate(Player player) {
        return data.computeIfAbsent(player.getUniqueId(), uuid -> {
            Vector3d position = new Vector3d(
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ());
            PhysicsState initialState = new PhysicsState(
                    position, Vector3d.ZERO, player.isOnGround(), player.isSneaking(), player.isSprinting(),
                    false, 0, 0, 0);
            return new PlayerData(uuid, initialState);
        });
    }

    public PlayerData get(UUID uuid) {
        return data.get(uuid);
    }

    public void remove(UUID uuid) {
        data.remove(uuid);
    }
}
