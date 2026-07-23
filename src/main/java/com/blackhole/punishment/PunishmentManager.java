package com.blackhole.punishment;

import com.blackhole.data.EntityHistoryManager;
import com.blackhole.data.EntitySnapshot;
import com.blackhole.data.PlayerData;
import com.blackhole.storage.dao.PunishmentDao;
import com.blackhole.storage.dao.ReplayDao;
import com.blackhole.storage.dao.ViolationDao;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every flag that survives a check's own grace-tick buffer lands here
 * (section 9): logged to SQLite always, then routed through the 3-tier
 * ladder (silent setback / staff alert / punish-command) plus the
 * flag-rate failsafe. The flagged player never sees a message, sound, or
 * title regardless of which tier fires - punish commands only ever use the
 * generic, configured message.
 */
public final class PunishmentManager {

    private static final double DEFAULT_ALERT_VL = 10.0;
    private static final double DEFAULT_PUNISH_VL = 30.0;
    private static final String DEFAULT_PUNISH_COMMAND = "kick {player} Has sido expulsado del servidor";
    private static final Set<String> MOVEMENT_RELATED_CHECKS = new HashSet<>(Arrays.asList(
            "Speed", "Fly", "Jesus", "Spider", "NoSlow", "AntiKnockback"));

    private final Plugin plugin;
    private final EntityHistoryManager entityHistoryManager;
    private final ViolationDao violationDao;
    private final PunishmentDao punishmentDao;
    private final ReplayDao replayDao;
    private final FlagRateTracker flagRateTracker = new FlagRateTracker();
    private final Set<UUID> alertsDisabled = ConcurrentHashMap.newKeySet();

    public PunishmentManager(Plugin plugin, EntityHistoryManager entityHistoryManager, ViolationDao violationDao,
                              PunishmentDao punishmentDao, ReplayDao replayDao) {
        this.plugin = plugin;
        this.entityHistoryManager = entityHistoryManager;
        this.violationDao = violationDao;
        this.punishmentDao = punishmentDao;
        this.replayDao = replayDao;
    }

    public void onFlag(Player player, PlayerData data, String checkName, double vlAmount, String details) {
        double vl = data.addViolationLevel(checkName, vlAmount);
        long nowMillis = System.currentTimeMillis();

        violationDao.insert(player.getUniqueId(), checkName, vl, nowMillis, buildEvidenceJson(player.getUniqueId()));

        int flagCount = flagRateTracker.recordFlag(player.getUniqueId());
        int flagRateThreshold = plugin.getConfig().getInt("punishments.flag-rate.threshold", 18);
        if (flagCount >= flagRateThreshold) {
            autoBanByVolume(player, flagCount);
            return;
        }

        double alertVl = plugin.getConfig().getDouble("checks." + checkName + ".alert-vl", DEFAULT_ALERT_VL);
        double punishVl = plugin.getConfig().getDouble("checks." + checkName + ".punish-vl", DEFAULT_PUNISH_VL);
        String punishCommand = plugin.getConfig().getString("checks." + checkName + ".punish-command", DEFAULT_PUNISH_COMMAND);

        if (vl >= punishVl) {
            executePunishCommand(player, checkName, punishCommand, vl);
            data.setViolationLevel(checkName, 0.0);
            return;
        }

        if (vl >= alertVl) {
            broadcastAlert(player, checkName, vl, details);
        }

        if (MOVEMENT_RELATED_CHECKS.contains(checkName)) {
            silentSetback(player, data);
        }
    }

    public void toggleAlerts(UUID uuid) {
        if (!alertsDisabled.remove(uuid)) {
            alertsDisabled.add(uuid);
        }
    }

    public boolean hasAlertsEnabled(UUID uuid) {
        return !alertsDisabled.contains(uuid);
    }

    private void silentSetback(Player player, PlayerData data) {
        com.blackhole.util.Vector3d target = data.getLastValidPosition();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Location current = player.getLocation();
            Location destination = new Location(current.getWorld(), target.x, target.y, target.z,
                    current.getYaw(), current.getPitch());
            player.teleport(destination);
        });
    }

    private void broadcastAlert(Player flagged, String checkName, double vl, String details) {
        boolean broadcastEnabled = plugin.getConfig().getBoolean("alerts.broadcast-enabled", true);
        boolean consoleLogEnabled = plugin.getConfig().getBoolean("alerts.console-log-enabled", true);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (broadcastEnabled) {
                TextComponent message = new TextComponent(String.format("[Blackhole] %s fallo %s (VL %.1f)",
                        flagged.getName(), checkName, vl));
                message.setColor(ChatColor.RED);

                TextComponent tpButton = new TextComponent(" [TP]");
                tpButton.setColor(ChatColor.YELLOW);
                tpButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ac tp " + flagged.getName()));
                tpButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder("Teletransportarse en modo espectador para verificar").create()));
                message.addExtra(tpButton);

                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.hasPermission("anticheat.alerts") && hasAlertsEnabled(online.getUniqueId())) {
                        online.spigot().sendMessage(message);
                    }
                }
            }
            if (consoleLogEnabled) {
                plugin.getLogger().info(String.format("[Blackhole] %s -> %s VL=%.2f (%s)", flagged.getName(), checkName, vl, details));
            }
        });
    }

    private void executePunishCommand(Player player, String checkName, String punishCommand, double vl) {
        punishmentDao.insert(player.getUniqueId(), "CHECK_PUNISH",
                "Check " + checkName + " alcanzo VL " + String.format("%.2f", vl), System.currentTimeMillis(), 0);
        Bukkit.getScheduler().runTask(plugin, () -> {
            String command = punishCommand.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        });
    }

    private void autoBanByVolume(Player player, int flagCount) {
        long durationSeconds = plugin.getConfig().getLong("punishments.flag-rate.ban-duration-seconds", 86_400L);

        violationDao.insert(player.getUniqueId(), "FlagRateTracker", flagCount, System.currentTimeMillis(),
                        buildEvidenceJson(player.getUniqueId()))
                .whenComplete((violationId, error) -> {
                    if (violationId != null && violationId > 0) {
                        persistReplaySnapshots(violationId, player.getUniqueId());
                    }
                });

        punishmentDao.insert(player.getUniqueId(), "AUTO_BAN_VOLUME",
                flagCount + " flags combinados en 5 minutos", System.currentTimeMillis(), durationSeconds);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Date expiry = new Date(System.currentTimeMillis() + durationSeconds * 1000L);
            Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), "Has sido expulsado del servidor", expiry, "Blackhole");
            player.kickPlayer("Has sido expulsado del servidor");
        });
    }

    private void persistReplaySnapshots(long violationId, UUID uuid) {
        List<EntitySnapshot> snapshots = entityHistoryManager.getRecentSnapshots(uuid);
        List<ReplayDao.Row> rows = new ArrayList<>();
        int tick = snapshots.size();
        for (EntitySnapshot snapshot : snapshots) {
            rows.add(new ReplayDao.Row(tick--, snapshot.getPosition().x, snapshot.getPosition().y, snapshot.getPosition().z,
                    snapshot.getYaw(), snapshot.getPitch(), false));
        }
        replayDao.insertSnapshots(violationId, rows);
    }

    private String buildEvidenceJson(UUID uuid) {
        List<EntitySnapshot> snapshots = entityHistoryManager.getRecentSnapshots(uuid);
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < snapshots.size(); i++) {
            EntitySnapshot snapshot = snapshots.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append(String.format(
                    "{\"x\":%.3f,\"y\":%.3f,\"z\":%.3f,\"yaw\":%.2f,\"pitch\":%.2f}",
                    snapshot.getPosition().x, snapshot.getPosition().y, snapshot.getPosition().z,
                    snapshot.getYaw(), snapshot.getPitch()));
        }
        json.append(']');
        return json.toString();
    }
}
