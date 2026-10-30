package me.newgen.team.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Loads the split configuration files:
 * config.yml (language), settings.yml, levels.yml, homes.yml, chest.yml, database.yml.
 * Each file is copied from the jar defaults on first run.
 */
public final class ConfigManager {

    private final Plugin plugin;
    private final ChestConfig chestConfig = new ChestConfig();
    private final TierConfig tierConfig = new TierConfig();

    // Volatile: load() runs on the main thread while these are read by the async
    // heartbeat (chest flush) and LogService on other threads.
    private volatile String language = "vi_VN";

    private volatile int maxNameLength = 16;
    private volatile int maxTagLength = 6;
    private volatile int maxMembers = 30;
    private volatile long inviteTtlSeconds = 120;

    private volatile int homeTeleportCooldownSeconds = 5;
    private volatile int homeTeleportWarmupSeconds = 3;
    private volatile boolean homeWarmupCancelOnMove = true;

    private volatile long chestFlushIntervalSeconds = 300;

    private volatile boolean trackKills = true;

    private volatile String teamChatFormat = "&d[%tag%] &7%player%&8: &f%message%";
    private volatile boolean teamChatLogToConsole = true;

    /** How text prompts (rename, tag, bank, search...) are shown to players. */
    public enum InputMethod { AUTO, DIALOG, SIGN }
    private volatile InputMethod inputMethod = InputMethod.AUTO;

    private volatile FileConfiguration database = new YamlConfiguration();
    private volatile FileConfiguration hooks = new YamlConfiguration();
    private volatile FileConfiguration logs = new YamlConfiguration();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        language = plugin.getConfig().getString("language", language);

        FileConfiguration settings = loadYaml("settings.yml");
        // Clamped to the database column width (VARCHAR(64)).
        maxNameLength = clamp(settings.getInt("team.max-name-length", maxNameLength), 1, 64);
        maxTagLength = clamp(settings.getInt("team.max-tag-length", maxTagLength), 1, 64);
        maxMembers = settings.getInt("team.max-members", maxMembers);
        inviteTtlSeconds = settings.getLong("team.invite-ttl-seconds", inviteTtlSeconds);
        trackKills = settings.getBoolean("stats.track-kills", trackKills);
        teamChatFormat = settings.getString("chat.team-format", teamChatFormat);
        teamChatLogToConsole = settings.getBoolean("chat.log-to-console", teamChatLogToConsole);
        inputMethod = parseInputMethod(settings.getString("gui.input-method", "AUTO"));

        FileConfiguration homes = loadYaml("homes.yml");
        homeTeleportCooldownSeconds = homes.getInt("home.teleport-cooldown-seconds", homeTeleportCooldownSeconds);
        homeTeleportWarmupSeconds = homes.getInt("home.teleport-warmup-seconds", homeTeleportWarmupSeconds);
        homeWarmupCancelOnMove = homes.getBoolean("home.warmup-cancel-on-move", homeWarmupCancelOnMove);

        FileConfiguration levels = loadYaml("levels.yml");
        tierConfig.load(levels.getConfigurationSection("tiers"));

        FileConfiguration chest = loadYaml("chest.yml");
        chestFlushIntervalSeconds = chest.getLong("chest.flush-interval-seconds", chestFlushIntervalSeconds);
        chestConfig.load(chest.getConfigurationSection("chest"));

        chestConfig.syncFromTiers(tierConfig);

        database = loadYaml("database.yml");
        hooks = loadYaml("hooks.yml");
        logs = loadYaml("logs.yml");
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static InputMethod parseInputMethod(String raw) {
        if (raw == null) return InputMethod.AUTO;
        switch (raw.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "DIALOG": case "DIALOGS": return InputMethod.DIALOG;
            case "SIGN": case "SIGNS": case "FAKE_SIGN": return InputMethod.SIGN;
            default: return InputMethod.AUTO;
        }
    }

    /**
     * Copies the default file from the jar on first run, then loads it.
     */
    private FileConfiguration loadYaml(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public String language() { return language; }

    public ChestConfig chestConfig() { return chestConfig; }
    public TierConfig tierConfig() { return tierConfig; }

    /** Raw database.yml (consumed by the storage layer). */
    public FileConfiguration database() { return database; }

    /** true when the given hook is enabled in hooks.yml (default: true). */
    public boolean hookEnabled(String name) {
        return hooks.getBoolean("hooks." + name, true);
    }

    /** true when file logging is enabled globally AND for the given category (logs.yml). */
    public boolean logEnabled(String category) {
        return logs.getBoolean("logs.enabled", true) && logs.getBoolean("logs." + category, true);
    }

    /** Debug mode toggle from logs.yml. */
    public boolean debug() {
        return logs.getBoolean("debug", false);
    }

    public int maxNameLength() { return maxNameLength; }
    public int maxTagLength() { return maxTagLength; }
    public int maxMembers() { return maxMembers; }
    public long inviteTtlSeconds() { return inviteTtlSeconds; }
    public int homeTeleportCooldownSeconds() { return homeTeleportCooldownSeconds; }
    public int homeTeleportWarmupSeconds() { return homeTeleportWarmupSeconds; }
    public boolean homeWarmupCancelOnMove() { return homeWarmupCancelOnMove; }
    public long chestFlushIntervalSeconds() { return chestFlushIntervalSeconds; }
    public boolean trackKills() { return trackKills; }
    public String teamChatFormat() { return teamChatFormat; }
    public boolean teamChatLogToConsole() { return teamChatLogToConsole; }
    public InputMethod inputMethod() { return inputMethod; }
}
