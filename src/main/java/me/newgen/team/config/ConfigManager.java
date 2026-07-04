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

    private String language = "vi_VN";

    private int maxNameLength = 16;
    private int maxTagLength = 6;
    private int maxMembers = 30;
    private long inviteTtlSeconds = 120;

    private int homeTeleportCooldownSeconds = 5;
    private int homeTeleportWarmupSeconds = 3;
    private boolean homeWarmupCancelOnMove = true;

    private long chestFlushIntervalSeconds = 300;

    private boolean trackKills = true;

    private FileConfiguration database = new YamlConfiguration();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        language = plugin.getConfig().getString("language", language);

        FileConfiguration settings = loadYaml("settings.yml");
        maxNameLength = settings.getInt("team.max-name-length", maxNameLength);
        maxTagLength = settings.getInt("team.max-tag-length", maxTagLength);
        maxMembers = settings.getInt("team.max-members", maxMembers);
        inviteTtlSeconds = settings.getLong("team.invite-ttl-seconds", inviteTtlSeconds);
        trackKills = settings.getBoolean("stats.track-kills", trackKills);

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

    public int maxNameLength() { return maxNameLength; }
    public int maxTagLength() { return maxTagLength; }
    public int maxMembers() { return maxMembers; }
    public long inviteTtlSeconds() { return inviteTtlSeconds; }
    public int homeTeleportCooldownSeconds() { return homeTeleportCooldownSeconds; }
    public int homeTeleportWarmupSeconds() { return homeTeleportWarmupSeconds; }
    public boolean homeWarmupCancelOnMove() { return homeWarmupCancelOnMove; }
    public long chestFlushIntervalSeconds() { return chestFlushIntervalSeconds; }
    public boolean trackKills() { return trackKills; }
}
