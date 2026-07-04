package me.newgen.team.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

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

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        language = c.getString("language", language);

        maxNameLength = c.getInt("team.max-name-length", maxNameLength);
        maxTagLength = c.getInt("team.max-tag-length", maxTagLength);
        maxMembers = c.getInt("team.max-members", maxMembers);
        inviteTtlSeconds = c.getLong("team.invite-ttl-seconds", inviteTtlSeconds);

        homeTeleportCooldownSeconds = c.getInt("home.teleport-cooldown-seconds", homeTeleportCooldownSeconds);
        homeTeleportWarmupSeconds = c.getInt("home.teleport-warmup-seconds", homeTeleportWarmupSeconds);
        homeWarmupCancelOnMove = c.getBoolean("home.warmup-cancel-on-move", homeWarmupCancelOnMove);

        chestFlushIntervalSeconds = c.getLong("chest.flush-interval-seconds", chestFlushIntervalSeconds);
        trackKills = c.getBoolean("stats.track-kills", trackKills);

        tierConfig.load(c.getConfigurationSection("tiers"));
        chestConfig.load(c.getConfigurationSection("chest"));

        chestConfig.syncFromTiers(tierConfig);
    }

    public String language() { return language; }

    public ChestConfig chestConfig() { return chestConfig; }
    public TierConfig tierConfig() { return tierConfig; }

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
