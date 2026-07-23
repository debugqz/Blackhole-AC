package com.blackhole.xray;

import com.blackhole.data.PlayerData;
import com.blackhole.data.PlayerDataManager;
import com.blackhole.punishment.PunishmentManager;
import com.blackhole.storage.dao.XrayStatsDao;
import com.blackhole.util.Vector3d;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Independent of movement/combat/build (section 8): analyzes mining patterns
 * over time via Bukkit's own BlockBreakEvent rather than raw packets - the
 * server already validated the block actually existed and was removed, so
 * there's no "client lied" concern the packet layer exists to guard against.
 * Combines all 3 signals into one VL; only a repeated blind reveal is
 * considered clear enough to flag alone.
 */
public final class XrayManager implements Listener {

    private static final Set<Material> VALUABLE_ORES = EnumSet.of(Material.DIAMOND_ORE, Material.EMERALD_ORE);
    private static final int BLIND_REVEAL_ALONE_THRESHOLD = 3;
    private static final double RATIO_MULTIPLIER_THRESHOLD = 3.0;
    private static final int MIN_SAMPLE_FOR_RATIO = 200;

    private final PlayerDataManager playerDataManager;
    private final PunishmentManager punishmentManager;
    private final XrayStatsDao xrayStatsDao;
    private final ExplorationTracker explorationTracker = new ExplorationTracker();
    private final XrayStatsManager statsManager = new XrayStatsManager();
    private final OreRevealCheck oreRevealCheck = new OreRevealCheck();
    private final VeinTrackingCheck veinTrackingCheck = new VeinTrackingCheck();
    private volatile boolean enabled = true;

    public XrayManager(PlayerDataManager playerDataManager, PunishmentManager punishmentManager, XrayStatsDao xrayStatsDao) {
        this.playerDataManager = playerDataManager;
        this.punishmentManager = punishmentManager;
        this.xrayStatsDao = xrayStatsDao;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!enabled) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission("anticheat.bypass")) {
            return;
        }
        PlayerData bypassCheckData = playerDataManager.get(player.getUniqueId());
        if (bypassCheckData != null && bypassCheckData.isExempt()) {
            return;
        }

        Block block = event.getBlock();
        UUID uuid = player.getUniqueId();
        boolean valuable = VALUABLE_ORES.contains(block.getType());

        double vlAmount = 0.0;
        StringBuilder details = new StringBuilder();

        if (valuable && oreRevealCheck.isBlindReveal(player, block, explorationTracker)) {
            int count = statsManager.recordBlindReveal(uuid);
            if (count >= BLIND_REVEAL_ALONE_THRESHOLD) {
                vlAmount += 3.0;
                details.append("blind-reveal-repetido=").append(count).append(' ');
            } else {
                vlAmount += 0.5;
                details.append("blind-reveal ");
            }
        }

        boolean straightTunnel = veinTrackingCheck.isSuspiciousTunnel(uuid,
                new Vector3d(block.getX(), block.getY(), block.getZ()));
        if (straightTunnel && valuable) {
            vlAmount += 1.0;
            details.append("tunel-recto-hacia-veta ");
        }

        statsManager.recordBlockMined(uuid, block.getType(), valuable);
        double ratio = statsManager.getPlayerRatio(uuid);
        double serverAverage = statsManager.getServerAverageRatio();
        if (statsManager.getOrCreate(uuid).getBlocksMined() >= MIN_SAMPLE_FOR_RATIO && serverAverage > 0
                && ratio > serverAverage * RATIO_MULTIPLIER_THRESHOLD) {
            vlAmount += 1.0;
            details.append("ratio=").append(String.format("%.4f", ratio))
                    .append(" promedio=").append(String.format("%.4f", serverAverage));
        }

        markExplored(block, uuid);

        XrayStatsManager.PlayerStats playerStats = statsManager.getOrCreate(uuid);
        int oresFound = 0;
        for (java.util.concurrent.atomic.AtomicInteger count : playerStats.getOresFound().values()) {
            oresFound += count.get();
        }
        xrayStatsDao.upsert(uuid, playerStats.getBlocksMined(), oresFound, ratio, playerStats.getBlindReveals());

        if (vlAmount <= 0.0) {
            return;
        }

        PlayerData data = playerDataManager.get(uuid);
        if (data == null) {
            return;
        }
        punishmentManager.onFlag(player, data, "Xray", vlAmount, details.toString().trim());
    }

    private void markExplored(Block block, UUID uuid) {
        explorationTracker.markExplored(uuid, block.getX(), block.getY(), block.getZ());
        explorationTracker.markExplored(uuid, block.getX(), block.getY() + 1, block.getZ());
        explorationTracker.markExplored(uuid, block.getX(), block.getY() - 1, block.getZ());
        explorationTracker.markExplored(uuid, block.getX() + 1, block.getY(), block.getZ());
        explorationTracker.markExplored(uuid, block.getX() - 1, block.getY(), block.getZ());
        explorationTracker.markExplored(uuid, block.getX(), block.getY(), block.getZ() + 1);
        explorationTracker.markExplored(uuid, block.getX(), block.getY(), block.getZ() - 1);
    }

    public void removePlayer(UUID uuid) {
        explorationTracker.remove(uuid);
        veinTrackingCheck.remove(uuid);
        statsManager.remove(uuid);
    }

    public XrayStatsManager getStatsManager() {
        return statsManager;
    }
}
