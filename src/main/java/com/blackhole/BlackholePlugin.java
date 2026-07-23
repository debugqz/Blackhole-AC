package com.blackhole;

import com.blackhole.check.CheckManager;
import com.blackhole.check.moves.NoFallCheck;
import com.blackhole.command.BlackholeCommand;
import com.blackhole.data.EntityHistoryManager;
import com.blackhole.data.PlayerData;
import com.blackhole.data.PlayerDataManager;
import com.blackhole.packet.PacketInjector;
import com.blackhole.packet.PacketListener;
import com.blackhole.packet.SyncManager;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.ChunkSnapshotCache;
import com.blackhole.physics.PredictionEngine;
import com.blackhole.punishment.PunishmentManager;
import com.blackhole.storage.Database;
import com.blackhole.storage.dao.PlayerDao;
import com.blackhole.storage.dao.PunishmentDao;
import com.blackhole.storage.dao.ReplayDao;
import com.blackhole.storage.dao.ViolationDao;
import com.blackhole.storage.dao.XrayStatsDao;
import com.blackhole.xray.XrayManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class BlackholePlugin extends JavaPlugin implements Listener {

    private static final long ENTITY_SAMPLE_PERIOD_TICKS = 1L;
    private static final long PING_SAMPLE_PERIOD_TICKS = 20L;

    private BlockPhysicsRegistry blockPhysicsRegistry;
    private PredictionEngine predictionEngine;
    private PlayerDataManager playerDataManager;
    private ChunkSnapshotCache chunkSnapshotCache;
    private SyncManager syncManager;
    private CheckManager checkManager;
    private PacketListener packetListener;
    private PacketInjector packetInjector;
    private EntityHistoryManager entityHistoryManager;
    private XrayManager xrayManager;
    private Database database;
    private PlayerDao playerDao;
    private ViolationDao violationDao;
    private PunishmentManager punishmentManager;
    private BlackholeCommand antiCheatCommand;
    private NoFallCheck noFallCheck;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.database = new Database();
        try {
            database.connect(getDataFolder());
        } catch (SQLException e) {
            getLogger().severe("No se pudo conectar a SQLite, deshabilitando el plugin: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.playerDao = new PlayerDao(database, getLogger());
        this.violationDao = new ViolationDao(database, getLogger());
        PunishmentDao punishmentDao = new PunishmentDao(database, getLogger());
        ReplayDao replayDao = new ReplayDao(database, getLogger());
        XrayStatsDao xrayStatsDao = new XrayStatsDao(database, getLogger());

        this.blockPhysicsRegistry = new BlockPhysicsRegistry();
        this.predictionEngine = new PredictionEngine();
        this.predictionEngine.setEpsilon(getConfig().getDouble("physics.epsilon", 0.03));
        this.playerDataManager = new PlayerDataManager();
        this.chunkSnapshotCache = new ChunkSnapshotCache();
        this.syncManager = new SyncManager();
        this.entityHistoryManager = new EntityHistoryManager();
        this.punishmentManager = new PunishmentManager(this, entityHistoryManager, violationDao, punishmentDao, replayDao);
        this.checkManager = new CheckManager(predictionEngine, punishmentManager);
        this.packetListener = new PacketListener(this, playerDataManager, predictionEngine, chunkSnapshotCache,
                blockPhysicsRegistry, checkManager, syncManager, entityHistoryManager);
        this.packetInjector = new PacketInjector(this, packetListener);

        this.noFallCheck = new NoFallCheck(playerDataManager);
        this.xrayManager = new XrayManager(playerDataManager, punishmentManager, xrayStatsDao);
        applyRuntimeConfig();

        this.antiCheatCommand = new BlackholeCommand(this, playerDataManager, punishmentManager, violationDao);
        getCommand("anticheat").setExecutor(antiCheatCommand);
        getCommand("anticheat").setTabCompleter(antiCheatCommand);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(chunkSnapshotCache, this);
        getServer().getPluginManager().registerEvents(noFallCheck, this);
        getServer().getPluginManager().registerEvents(xrayManager, this);

        getServer().getScheduler().runTaskTimer(this, this::sampleEntityHistory, ENTITY_SAMPLE_PERIOD_TICKS, ENTITY_SAMPLE_PERIOD_TICKS);
        getServer().getScheduler().runTaskTimer(this, this::pingOnlinePlayers, PING_SAMPLE_PERIOD_TICKS, PING_SAMPLE_PERIOD_TICKS);

        getLogger().info("Blackhole habilitado (fase 7: comandos, permisos y pulido final).");
    }

    /** Re-reads the on/off toggles from config.yml - called on enable and on /ac reload. */
    public void applyRuntimeConfig() {
        checkManager.applyConfig(getConfig());
        noFallCheck.setEnabled(getConfig().getBoolean("checks.NoFall.enabled", true));
        xrayManager.setEnabled(getConfig().getBoolean("checks.Xray.enabled", true));
    }

    @Override
    public void onDisable() {
        for (Player player : getServer().getOnlinePlayers()) {
            packetInjector.uninject(player);
        }
        if (database != null) {
            database.shutdown();
        }
        getLogger().info("Blackhole deshabilitado.");
    }

    private void sampleEntityHistory() {
        long now = System.nanoTime();
        for (Player player : getServer().getOnlinePlayers()) {
            entityHistoryManager.record(player, now);
            for (Entity nearby : player.getNearbyEntities(16, 16, 16)) {
                if (nearby instanceof LivingEntity) {
                    entityHistoryManager.record(nearby, now);
                }
            }
        }
    }

    private void pingOnlinePlayers() {
        for (Player player : getServer().getOnlinePlayers()) {
            syncManager.sendTransaction(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerDataManager.getOrCreate(player);
        packetInjector.inject(player);
        playerDao.upsert(player.getUniqueId(), player.getName(), System.currentTimeMillis());

        PlayerData data = playerDataManager.get(player.getUniqueId());
        ItemStack item = player.getInventory().getItemInHand();
        data.setHeldItemKnockbackLevel(item != null ? item.getEnchantmentLevel(Enchantment.KNOCKBACK) : 0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        packetInjector.uninject(event.getPlayer());
        playerDataManager.remove(event.getPlayer().getUniqueId());
        syncManager.remove(event.getPlayer().getUniqueId());
        entityHistoryManager.remove(event.getPlayer().getUniqueId());
        xrayManager.removePlayer(event.getPlayer().getUniqueId());
    }

    public BlockPhysicsRegistry getBlockPhysicsRegistry() {
        return blockPhysicsRegistry;
    }

    public PredictionEngine getPredictionEngine() {
        return predictionEngine;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public ChunkSnapshotCache getChunkSnapshotCache() {
        return chunkSnapshotCache;
    }

    public CheckManager getCheckManager() {
        return checkManager;
    }

    public EntityHistoryManager getEntityHistoryManager() {
        return entityHistoryManager;
    }

    public XrayManager getXrayManager() {
        return xrayManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }

    public Database getBlackholeDatabase() {
        return database;
    }

    public ViolationDao getViolationDao() {
        return violationDao;
    }
}
