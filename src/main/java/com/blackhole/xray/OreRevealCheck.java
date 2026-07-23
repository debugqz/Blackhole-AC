package com.blackhole.xray;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

/**
 * Blind ore reveal (section 8, signal 1): true only when NONE of the broken
 * ore's 6 neighbor faces were already explored - i.e. there was no legitimate
 * way the player could have known the ore was there before breaking straight
 * into it.
 */
public final class OreRevealCheck {

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    public boolean isBlindReveal(Player player, Block block, ExplorationTracker tracker) {
        for (BlockFace face : FACES) {
            Block neighbor = block.getRelative(face);
            if (tracker.isExplored(player.getUniqueId(), neighbor.getX(), neighbor.getY(), neighbor.getZ())) {
                return false;
            }
        }
        return true;
    }
}
