package com.blackhole.physics;

import org.bukkit.Material;

/**
 * Thread-safe read access to block data for the prediction engine. Real
 * implementation (phase 2+) is backed by a per-chunk snapshot cache kept in
 * sync from the main thread (see section 11) - never a live World read from
 * off the main thread.
 */
public interface BlockSnapshotProvider {

    Material getMaterial(int blockX, int blockY, int blockZ);

    default BlockPhysicsProfile getProfile(BlockPhysicsRegistry registry, int blockX, int blockY, int blockZ) {
        return registry.getProfile(getMaterial(blockX, blockY, blockZ));
    }
}
