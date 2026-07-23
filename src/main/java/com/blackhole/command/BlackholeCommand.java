package com.blackhole.command;

import com.blackhole.BlackholePlugin;
import com.blackhole.data.PlayerData;
import com.blackhole.data.PlayerDataManager;
import com.blackhole.punishment.PunishmentManager;
import com.blackhole.storage.dao.ViolationDao;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class BlackholeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("reload", "alerts", "vl", "exempt", "tp", "history");

    private final BlackholePlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final PunishmentManager punishmentManager;
    private final ViolationDao violationDao;

    public BlackholeCommand(BlackholePlugin plugin, PlayerDataManager playerDataManager,
                             PunishmentManager punishmentManager, ViolationDao violationDao) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.punishmentManager = punishmentManager;
        this.violationDao = violationDao;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                return handleReload(sender);
            case "alerts":
                return handleAlerts(sender);
            case "vl":
                return handleVl(sender, args);
            case "exempt":
                return handleExempt(sender, args);
            case "tp":
                return handleTp(sender, args);
            case "history":
                return handleHistory(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "/ac <reload|alerts|vl|exempt|tp|history>");
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("anticheat.admin")) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + "No tienes permiso para eso.");
        return false;
    }

    private boolean handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return true;
        }
        plugin.reloadConfig();
        plugin.getPredictionEngine().setEpsilon(plugin.getConfig().getDouble("physics.epsilon", 0.03));
        plugin.applyRuntimeConfig();
        sender.sendMessage(ChatColor.GREEN + "config.yml recargado.");
        return true;
    }

    private boolean handleAlerts(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden usar esto.");
            return true;
        }
        if (!sender.hasPermission("anticheat.alerts")) {
            sender.sendMessage(ChatColor.RED + "No tienes permiso para eso.");
            return true;
        }
        Player player = (Player) sender;
        punishmentManager.toggleAlerts(player.getUniqueId());
        boolean enabled = punishmentManager.hasAlertsEnabled(player.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + "Alertas " + (enabled ? "activadas" : "desactivadas") + ".");
        return true;
    }

    private boolean handleVl(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /ac vl <jugador>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado o desconectado.");
            return true;
        }
        PlayerData data = playerDataManager.get(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(ChatColor.RED + "Sin datos para ese jugador.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "VL de " + target.getName() + ":");
        boolean any = false;
        for (Map.Entry<String, Double> entry : data.getViolationLevels().entrySet()) {
            if (entry.getValue() > 0.0) {
                any = true;
                sender.sendMessage(ChatColor.YELLOW + " - " + entry.getKey() + ": " + String.format("%.2f", entry.getValue()));
            }
        }
        if (!any) {
            sender.sendMessage(ChatColor.GRAY + " (sin violaciones activas)");
        }
        return true;
    }

    private boolean handleExempt(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /ac exempt <jugador> <segundos>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado o desconectado.");
            return true;
        }
        long seconds;
        try {
            seconds = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Segundos invalidos.");
            return true;
        }
        PlayerData data = playerDataManager.get(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(ChatColor.RED + "Sin datos para ese jugador.");
            return true;
        }
        data.exemptFor(seconds);
        sender.sendMessage(ChatColor.GREEN + target.getName() + " exento de todos los checks por " + seconds + "s.");
        return true;
    }

    private boolean handleTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden usar esto.");
            return true;
        }
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /ac tp <jugador>");
            return true;
        }
        Player staff = (Player) sender;
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado o desconectado.");
            return true;
        }
        staff.setGameMode(GameMode.SPECTATOR);
        staff.teleport(target.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Teletransportado junto a " + target.getName() + " en modo espectador.");
        return true;
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /ac history <jugador>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado o desconectado (solo jugadores en linea por ahora).");
            return true;
        }

        String targetName = target.getName();
        violationDao.findRecent(target.getUniqueId(), 10).thenAccept(rows -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (rows.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "Sin historial de violaciones para " + targetName + ".");
                return;
            }
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sender.sendMessage(ChatColor.GOLD + "Ultimas violaciones de " + targetName + ":");
            for (ViolationDao.Row row : rows) {
                sender.sendMessage(ChatColor.YELLOW + " - " + row.checkName + " VL=" + String.format("%.2f", row.vlAtTime)
                        + " @ " + format.format(new Date(row.timestampMillis)));
            }
        }));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> matches = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    matches.add(sub);
                }
            }
            return matches;
        }
        if (args.length == 2 && Arrays.asList("vl", "exempt", "tp", "history").contains(args[0].toLowerCase())) {
            List<String> matches = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    matches.add(player.getName());
                }
            }
            return matches;
        }
        return java.util.Collections.emptyList();
    }
}
