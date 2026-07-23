package com.blackhole.xray;

import org.bukkit.Material;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Per-player and server-wide mined-vs-ore ratios (section 8, signal 2). */
public final class XrayStatsManager {

    public static final class PlayerStats {
        private final AtomicInteger blocksMined = new AtomicInteger();
        private final Map<Material, AtomicInteger> oresFound = new ConcurrentHashMap<>();
        private final AtomicInteger blindReveals = new AtomicInteger();

        public int getBlocksMined() {
            return blocksMined.get();
        }

        public int getBlindReveals() {
            return blindReveals.get();
        }

        public Map<Material, AtomicInteger> getOresFound() {
            return oresFound;
        }
    }

    private final Map<UUID, PlayerStats> stats = new ConcurrentHashMap<>();
    private final AtomicLong globalBlocksMined = new AtomicLong();
    private final AtomicLong globalOresFound = new AtomicLong();

    public PlayerStats getOrCreate(UUID uuid) {
        return stats.computeIfAbsent(uuid, u -> new PlayerStats());
    }

    public void recordBlockMined(UUID uuid, Material material, boolean valuable) {
        PlayerStats playerStats = getOrCreate(uuid);
        playerStats.blocksMined.incrementAndGet();
        globalBlocksMined.incrementAndGet();
        if (valuable) {
            playerStats.oresFound.computeIfAbsent(material, m -> new AtomicInteger()).incrementAndGet();
            globalOresFound.incrementAndGet();
        }
    }

    public int recordBlindReveal(UUID uuid) {
        return getOrCreate(uuid).blindReveals.incrementAndGet();
    }

    public double getPlayerRatio(UUID uuid) {
        PlayerStats playerStats = getOrCreate(uuid);
        int mined = playerStats.blocksMined.get();
        if (mined == 0) {
            return 0.0;
        }
        int ores = 0;
        for (AtomicInteger count : playerStats.oresFound.values()) {
            ores += count.get();
        }
        return (double) ores / mined;
    }

    public double getServerAverageRatio() {
        long mined = globalBlocksMined.get();
        if (mined == 0) {
            return 0.0;
        }
        return (double) globalOresFound.get() / mined;
    }

    public void remove(UUID uuid) {
        stats.remove(uuid);
    }
}
