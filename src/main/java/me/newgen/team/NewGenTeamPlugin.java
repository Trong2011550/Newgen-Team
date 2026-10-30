package me.newgen.team;

import me.newgen.team.command.TeamCommand;
import me.newgen.team.config.ConfigManager;
import me.newgen.team.config.LanguageManager;
import me.newgen.team.config.MessageManager;
import me.newgen.team.config.ThemeManager;
import me.newgen.team.gui.MenuLayoutManager;
import me.newgen.team.gui.MenuManager;
import me.newgen.team.hook.PlaceholderHook;
import me.newgen.team.hook.PlayerPointsHook;
import me.newgen.team.hook.VaultHook;
import me.newgen.team.listener.ChatListener;
import me.newgen.team.listener.CombatListener;
import me.newgen.team.listener.InventoryListener;
import me.newgen.team.listener.JoinQuitListener;
import me.newgen.team.permission.PermissionManager;
import me.newgen.team.scheduler.Schedulers;
import me.newgen.team.service.AdminService;
import me.newgen.team.service.AuditLogService;
import me.newgen.team.service.LogService;
import me.newgen.team.service.ChatService;
import me.newgen.team.service.SignInputService;
import me.newgen.team.service.ChestService;
import me.newgen.team.service.SearchManager;
import me.newgen.team.service.HomeService;
import me.newgen.team.service.RelationService;
import me.newgen.team.service.StatsService;
import me.newgen.team.service.TeamService;
import me.newgen.team.service.TierService;
import me.newgen.team.storage.DatabaseSettings;
import me.newgen.team.storage.JdbcStorage;
import me.newgen.team.storage.StorageProvider;
import com.github.retrooper.packetevents.PacketEvents;
import org.bstats.bukkit.Metrics;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class NewGenTeamPlugin extends JavaPlugin {

    /** bStats plugin id: https://bstats.org/plugin/bukkit/NewGenTeam */
    private static final int BSTATS_PLUGIN_ID = 32387;

    private Metrics metrics;
    private volatile long lastChestFlush = System.currentTimeMillis();

    private ConfigManager configManager;
    private ThemeManager themeManager;
    private LanguageManager languageManager;
    private MessageManager messageManager;

    private StorageProvider storage;
    private VaultHook pointsHook;
    private PlayerPointsHook playerPointsHook;
    private PlaceholderHook placeholderHook;

    private PermissionManager permissionManager;
    private TeamService teamService;
    private ChestService chestService;
    private TierService tierService;
    private HomeService homeService;
    private ChatService chatService;
    private StatsService statsService;
    private RelationService relationService;
    private SearchManager searchManager;
    private SignInputService signInputService;
    private me.newgen.team.service.DialogInputService dialogInputService;
    private AuditLogService auditLogService;
    private AdminService adminService;
    private LogService logService;

    private MenuLayoutManager menuLayoutManager;
    private MenuManager menuManager;

    @Override
    public void onEnable() {
        Schedulers.init(this);
        printStartupBanner();

        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();

        this.configManager = new ConfigManager(this);
        configManager.load();
        this.themeManager = new ThemeManager(this);
        this.languageManager = new LanguageManager(this);
        this.messageManager = new MessageManager(themeManager, languageManager);
        themeManager.load();
        languageManager.load(configManager.language());
        messageManager.load();

        me.newgen.team.gui.Icons.init(messageManager);

        this.logService = new LogService(getLogger(), configManager, getDataFolder());

        this.pointsHook = new VaultHook(getLogger());
        if (configManager.hookEnabled("vault")) pointsHook.hook(); else getLogger().info("Vault hook disabled in hooks.yml.");
        this.playerPointsHook = new PlayerPointsHook(getLogger());
        if (configManager.hookEnabled("playerpoints")) playerPointsHook.hook(); else getLogger().info("PlayerPoints hook disabled in hooks.yml.");

        DatabaseSettings dbSettings = new DatabaseSettings(configManager.database());
        this.storage = new JdbcStorage(getLogger(), getDataFolder(), dbSettings);

        this.permissionManager = new PermissionManager();
        this.teamService = new TeamService(storage);
        teamService.configure(configManager.maxNameLength(), configManager.maxTagLength(),
                configManager.maxMembers(), configManager.inviteTtlSeconds());
        teamService.setLogs(logService);
        this.chestService = new ChestService(getLogger(), storage, configManager.chestConfig());
        teamService.setChests(chestService);
        this.tierService = new TierService(configManager, playerPointsHook, chestService, storage);
        teamService.setTiers(tierService);
        tierService.setTeams(teamService);
        this.homeService = new HomeService(configManager, storage);
        homeService.setTeams(teamService);
        this.chatService = new ChatService(teamService, messageManager);
        chatService.setFormat(configManager.teamChatFormat());
        chatService.setLogToConsole(configManager.teamChatLogToConsole());
        this.statsService = new StatsService(teamService, storage);
        this.relationService = new RelationService(teamService, storage);
        teamService.setRelations(relationService);
        this.searchManager = new SearchManager(teamService);
        this.signInputService = new SignInputService();
        this.dialogInputService = new me.newgen.team.service.DialogInputService(messageManager, configManager);
        signInputService.setDialogs(dialogInputService);
        getLogger().info("Text input method='" + configManager.inputMethod() + "', Dialog API "
                + (dialogInputService.supported() ? "detected" : "unavailable (sign fallback)") + ".");
        this.auditLogService = new AuditLogService(storage, getLogger());
        this.adminService = new AdminService(this);

        PacketEvents.getAPI().init();
        PacketEvents.getAPI().getEventManager().registerListener(signInputService);

        this.menuLayoutManager = new MenuLayoutManager(this);
        menuLayoutManager.load();
        this.menuManager = new MenuManager(this);

        storage.init().whenComplete((v, ex) -> {
            if (ex != null) {
                getLogger().severe("Storage init failed; disabling plugin.");
                Schedulers.global(() -> getServer().getPluginManager().disablePlugin(this));
                return;
            }
            storage.loadAllTeams().whenComplete((teams, ex2) -> {
                if (ex2 != null) {
                    getLogger().severe("Failed to load teams: " + ex2.getMessage());
                    return;
                }
                Schedulers.global(() -> {
                    teamService.loadAll(teams);
                    getLogger().info("Loaded " + teams.size() + " teams.");
                });
            });
        });

        // PlaceholderHook extends PlaceholderAPI's PlaceholderExpansion. Constructing it
        // loads that superclass, which throws NoClassDefFoundError (aborting onEnable)
        // when PlaceholderAPI is not installed - so only touch the class when the plugin
        // is actually present.
        if (!configManager.hookEnabled("placeholderapi")) {
            getLogger().info("PlaceholderAPI hook disabled in hooks.yml.");
        } else if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI not installed - placeholders disabled.");
        } else {
            this.placeholderHook = new PlaceholderHook(this, teamService);
            if (placeholderHook.tryRegister()) {
                getLogger().info("Hooked into PlaceholderAPI.");
            }
        }

        TeamCommand teamCommand = new TeamCommand(this);
        me.newgen.team.command.NewGenCommand newGenCommand = new me.newgen.team.command.NewGenCommand(this);
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    "team",
                    "Open the NewGen Team menu.",
                    java.util.List.of("t", "faction", "f", "guild", "g"),
                    new me.newgen.team.command.TeamBasicCommand(teamCommand));
            event.registrar().register(
                    "newgenteam",
                    "NewGen Team admin & info commands.",
                    java.util.List.of("newgen", "ngen", "ngt"),
                    new me.newgen.team.command.NewGenBasicCommand(newGenCommand));
        });

        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new me.newgen.team.listener.MoveListener(this), this);

        // Heartbeat reads the interval live so /team reload applies it without a restart.
        Schedulers.asyncRepeating(me.newgen.team.util.ResourceMetrics.measured(() -> {
            statsService.flushDirty();
            long now = System.currentTimeMillis();
            long interval = Math.max(30, configManager.chestFlushIntervalSeconds()) * 1000L;
            if (now - lastChestFlush >= interval) {
                lastChestFlush = now;
                chestService.flushAll();
            }
        }), 30, 30);

        // Expired invites, join requests, ally requests and cooldowns.
        Schedulers.asyncRepeating(me.newgen.team.util.ResourceMetrics.measured(() -> {
            teamService.sweepExpired();
            homeService.sweepExpired();
            relationService.sweepExpired();
        }), 60, 60);

        if (BSTATS_PLUGIN_ID > 0) {
            this.metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        } else {
            getLogger().warning("bStats disabled: set BSTATS_PLUGIN_ID after registering the plugin at bstats.org.");
        }

        getComponentLogger().info(net.kyori.adventure.text.Component.text(
                "Database: " + dbSettings.type(), net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN));
    }

    /**
     * Prints the NewGen Studio banner as the very first thing in onEnable, before any
     * hook/service can throw. Previously it sat at the bottom of onEnable, so a failure
     * in a soft-dependency guard (e.g. missing PlaceholderAPI) aborted onEnable and the
     * banner never appeared.
     */
    private void printStartupBanner() {
        String software = Schedulers.isFolia() ? "Folia" : getServer().getName();
        var console = getComponentLogger();
        String[] banner = {
                " _   _                   ____              ",
                "| \\ | |  ___ __      __ / ___|  ___  _ __  ",
                "|  \\| | / _ \\\\ \\ /\\ / /| |  _  / _ \\| '_ \\ ",
                "| |\\  ||  __/ \\ V  V / | |_| ||  __/| | | |",
                "|_| \\_| \\___|  \\_/\\_/   \\____| \\___||_| |_|",
                "                S T U D I O                "
        };
        for (String line : banner) {
            console.info(net.kyori.adventure.text.Component.text(line, net.kyori.adventure.text.format.NamedTextColor.GREEN));
        }
        console.info(net.kyori.adventure.text.Component.text("Discord: https://discord.gg/twKAP7JjW", net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN));
        console.info(net.kyori.adventure.text.Component.text("Server: " + software + " " + getServer().getMinecraftVersion(), net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN));
    }

    @Override
    public void onDisable() {

        if (metrics != null) metrics.shutdown();
        if (chestService != null) chestService.saveAllOnShutdown();
        if (teamService != null) teamService.saveAll();
        if (storage != null) storage.shutdown();
        if (logService != null) logService.shutdown();
        try {
            if (PacketEvents.getAPI() != null) PacketEvents.getAPI().terminate();
        } catch (Exception ignored) {

        }
        getLogger().info("NewGen Team disabled.");
    }

    public void reload() {
        configManager.load();
        themeManager.load();
        languageManager.load(configManager.language());
        messageManager.load();
        menuLayoutManager.load();
        teamService.configure(configManager.maxNameLength(), configManager.maxTagLength(),
                configManager.maxMembers(), configManager.inviteTtlSeconds());
        chatService.setFormat(configManager.teamChatFormat());
        chatService.setLogToConsole(configManager.teamChatLogToConsole());
    }

    public ConfigManager config() { return configManager; }
    public MessageManager messages() { return messageManager; }
    public StorageProvider storage() { return storage; }
    public VaultHook points() { return pointsHook; }
    public PlayerPointsHook playerPoints() { return playerPointsHook; }
    public PlaceholderHook placeholders() { return placeholderHook; }
    public PermissionManager permissions() { return permissionManager; }
    public TeamService teams() { return teamService; }
    public ChestService chests() { return chestService; }
    public TierService tiers() { return tierService; }
    public HomeService homes() { return homeService; }
    public ChatService chat() { return chatService; }
    public StatsService stats() { return statsService; }
    public RelationService relations() { return relationService; }
    public SearchManager search() { return searchManager; }
    public SignInputService signInput() { return signInputService; }
    public AuditLogService auditLogs() { return auditLogService; }
    public AdminService admin() { return adminService; }
    public LogService logs() { return logService; }
    public MenuLayoutManager menuLayouts() { return menuLayoutManager; }
    public MenuManager menus() { return menuManager; }
}
