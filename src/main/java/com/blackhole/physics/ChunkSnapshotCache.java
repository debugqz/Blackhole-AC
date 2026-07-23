package com.blackhole.physics;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe block lookups for PredictionEngine (section 11): Bukkit's
 * ChunkSnapshot is documented safe to read off the main thread once taken, so
 * this caches one per loaded chunk and refreshes it only from main-thread
 * block-change events - the packet/Netty thread never touches World directly.
 */
public final class ChunkSnapshotCache implements BlockSnapshotProvider, Listener {

    private static final int PREWARM_RADIUS_CHUNKS = 3;

    private final ConcurrentMap<Long, ChunkSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public Material getMaterial(int blockX, int blockY, int blockZ) {
        if (blockY < 0 || blockY > 255) {
            return Material.AIR;
        }
        ChunkSnapshot snapshot = snapshots.get(key(blockX >> 4, blockZ >> 4));
        if (snapshot == null) {
            return Material.AIR;
        }
        int localX = blockX & 15;
        int localZ = blockZ & 15;
        return Material.getMaterial(snapshot.getBlockTypeId(localX, blockY, localZ));
    }

    /** Main-thread only. */
    public void refreshChunk(Chunk chunk) {
        snapshots.put(key(chunk.getX(), chunk.getZ()), chunk.getChunkSnapshot());
    }

    /** Main-thread only. */
    public void prewarmAround(Player player) {
        World world = player.getWorld();
        int centerX = player.getLocation().getBlockX() >> 4;
        int centerZ = player.getLocation().getBlockZ() >> 4;
        for (int dx = -PREWARM_RADIUS_CHUNKS; dx <= PREWARM_RADIUS_CHUNKS; dx++) {
            for (int dz = -PREWARM_RADIUS_CHUNKS; dz <= PREWARM_RADIUS_CHUNKS; dz++) {
                int chunkX = centerX + dx;
                int chunkZ = centerZ + dz;
                if (!snapshots.containsKey(key(chunkX, chunkZ)) && world.isChunkLoaded(chunkX, chunkZ)) {
                    refreshChunk(world.getChunkAt(chunkX, chunkZ));
                }
            }
        }
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        refreshChunk(event.getBlock().getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        refreshChunk(event.getBlock().getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        refreshChunk(event.getBlock().getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockFromTo(BlockFromToEvent event) {
        refreshChunk(event.getToBlock().getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        prewarmAround(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        prewarmAround(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() >> 4 != event.getTo().getBlockX() >> 4
                || event.getFrom().getBlockZ() >> 4 != event.getTo().getBlockZ() >> 4) {
            prewarmAround(event.getPlayer());
        }
    }
}
