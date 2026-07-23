package com.blackhole.xray;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player set of block positions the player has already seen (broken, or
 * directly adjacent to a break). Built incrementally as mining progresses -
 * each break reveals its 6 neighbor faces - rather than one upfront flood-fill.
 */
public final class ExplorationTracker {

    private final Map<UUID, Set<Long>> exploredByPlayer = new ConcurrentHashMap<>();

    public boolean isExplored(UUID uuid, int x, int y, int z) {
        Set<Long> explored = exploredByPlayer.get(uuid);
        return explored != null && explored.contains(key(x, y, z));
    }

    public void markExplored(UUID uuid, int x, int y, int z) {
        exploredByPlayer.computeIfAbsent(uuid, u -> ConcurrentHashMap.newKeySet()).add(key(x, y, z));
    }

    public void remove(UUID uuid) {
        exploredByPlayer.remove(uuid);
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
    }
}
