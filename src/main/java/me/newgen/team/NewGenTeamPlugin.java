package me.newgen.team;

import me.newgen.team.command.TeamCommand;
import me.newgen.team.config.ConfigManager;
import me.newgen.team.config.LanguageManager;
import me.newgen.team.config.MessageManager;
import me.newgen.team.config.ThemeManager;
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
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class NewGenTeamPlugin extends JavaPlugin {

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
    private AuditLogService auditLogService;
    private AdminService adminService;

    private MenuManager menuManager;

    @Override
    public void onEnable() {
        Schedulers.init(this);

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

        this.pointsHook = new VaultHook(getLogger());
        pointsHook.hook();
        this.playerPointsHook = new PlayerPointsHook(getLogger());
        playerPointsHook.hook();

        this.storage = new JdbcStorage(getLogger(), getDataFolder(),
                new DatabaseSettings(configManager.database()));

        this.permissionManager = new PermissionManager();
        this.teamService = new TeamService(storage);
        teamService.configure(configManager.maxNameLength(), configManager.maxTagLength(),
                configManager.maxMembers(), configManager.inviteTtlSeconds());
        this.chestService = new ChestService(getLogger(), storage, configManager.chestConfig());
        this.tierService = new TierService(configManager, playerPointsHook, chestService, storage);
        teamService.setTiers(tierService);
        this.homeService = new HomeService(configManager, storage);
        this.chatService = new ChatService(teamService, messageManager);
        this.statsService = new StatsService(teamService, storage);
        this.relationService = new RelationService(teamService, storage);
        this.searchManager = new SearchManager(teamService);
        this.signInputService = new SignInputService();
        this.auditLogService = new AuditLogService(storage);
        this.adminService = new AdminService(this);

        PacketEvents.getAPI().init();
        PacketEvents.getAPI().getEventManager().registerListener(signInputService);

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

        this.placeholderHook = new PlaceholderHook(this, teamService);
        if (placeholderHook.tryRegister()) {
            getLogger().info("Hooked into PlaceholderAPI.");
        }

        TeamCommand teamCommand = new TeamCommand(this);
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                    "team",
                    "Open the NewGen Team menu.",
                    java.util.List.of("t", "faction", "f", "guild", "g"),
                    new me.newgen.team.command.TeamBasicCommand(teamCommand));
        });

        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);

        long flush = Math.max(30, configManager.chestFlushIntervalSeconds());
        Schedulers.asyncRepeating(() -> chestService.flushAll(), flush, flush);

        getLogger().info("NewGen Team enabled (Folia=" + Schedulers.isFolia() + ").");
    }

    @Override
    public void onDisable() {

        if (chestService != null) chestService.saveAllOnShutdown();
        if (teamService != null) teamService.saveAll();
        if (storage != null) storage.shutdown();
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
        teamService.configure(configManager.maxNameLength(), configManager.maxTagLength(),
                configManager.maxMembers(), configManager.inviteTtlSeconds());
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
    public MenuManager menus() { return menuManager; }
}
