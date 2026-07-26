package me.newgen.team.service;

import me.newgen.team.config.ConfigManager;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamHome;
import me.newgen.team.scheduler.Schedulers;
import me.newgen.team.storage.StorageProvider;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class HomeService {

    private final ConfigManager config;
    private final StorageProvider storage;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    private final Map<UUID, Long> warmups = new ConcurrentHashMap<>();

    /** Warmup start positions, for warmup-cancel-on-move. */
    private final Map<UUID, Location> warmupOrigins = new ConcurrentHashMap<>();

    public HomeService(ConfigManager config, StorageProvider storage) {
        this.config = config;
        this.storage = storage;
    }

    private TeamService teams;

    public void setTeams(TeamService teams) {
        this.teams = teams;
    }

    /** Persist through TeamService.persist() so failures are logged, not swallowed. */
    private void persist(Team team) {
        if (teams != null) teams.persist(team);
        else storage.saveTeam(team);
    }

    /**
     * Technical key for the default home. Stored in the database; never localize
     * or change it, otherwise existing homes are orphaned.
     */
    public static final String DEFAULT_HOME_KEY = "default";

    public enum Result { SUCCESS, NO_HOME, ON_COOLDOWN, ALREADY_WARMING, WORLD_MISSING }

    public enum SetResult { SUCCESS, LIMIT_REACHED, NAME_TAKEN, INVALID_NAME }

    public SetResult setHome(Team team, Location loc, int homeLimit) {
        // Moving an existing default home does not consume a slot.
        boolean exists = team.namedHomes().containsKey(DEFAULT_HOME_KEY);
        if (!exists && team.namedHomes().size() >= homeLimit) return SetResult.LIMIT_REACHED;
        team.home(TeamHome.from(loc));

        team.namedHomes().put(DEFAULT_HOME_KEY, team.home());
        persist(team);
        return SetResult.SUCCESS;
    }

    public SetResult setNamedHome(Team team, String name, Location loc, int homeLimit) {
        if (name == null || name.isBlank() || name.length() > 24) return SetResult.INVALID_NAME;
        boolean exists = team.namedHomes().containsKey(name);
        if (!exists && team.namedHomes().size() >= homeLimit) return SetResult.LIMIT_REACHED;
        team.namedHomes().put(name, TeamHome.from(loc));

        if (team.home() == null) team.home(TeamHome.from(loc));
        persist(team);
        return SetResult.SUCCESS;
    }

    public boolean deleteNamedHome(Team team, String name) {
        TeamHome removed = team.namedHomes().remove(name);
        if (removed == null) return false;

        if (team.namedHomes().isEmpty()) {
            team.home(null);
        } else {
            team.home(team.namedHomes().values().iterator().next());
        }
        persist(team);
        return true;
    }

    public Result teleportNamed(Player player, Team team, String name,
                                Runnable onWarmupStart, Runnable onComplete, Runnable onCancelled) {
        TeamHome home = team.namedHomes().get(name);
        if (home == null) return Result.NO_HOME;
        Location dest = home.toLocation();
        if (dest == null) return Result.WORLD_MISSING;

        UUID id = player.getUniqueId();
        if (cooldownRemaining(id) > 0) return Result.ON_COOLDOWN;
        if (warmups.containsKey(id)) return Result.ALREADY_WARMING;

        startTeleport(player, id, dest, onWarmupStart, onComplete, onCancelled);
        return Result.SUCCESS;
    }

    /** Shared warmup/instant path for both the default and named home teleports. */
    private void startTeleport(Player player, UUID id, Location dest, Runnable onWarmupStart,
                               Runnable onComplete, Runnable onCancelled) {
        int warmupSec = config.homeTeleportWarmupSeconds();
        if (warmupSec <= 0) {
            armCooldown(id);
            doTeleport(player, dest, onComplete);
            return;
        }

        long token = System.nanoTime();
        warmups.put(id, token);
        warmupOrigins.put(id, player.getLocation());
        if (onWarmupStart != null) onWarmupStart.run();

        Schedulers.entityLater(player, () -> {
            Long current = warmups.get(id);
            if (current == null || current != token) {
                if (onCancelled != null) onCancelled.run();
                return;
            }
            warmups.remove(id);
            warmupOrigins.remove(id);
            armCooldown(id);
            doTeleport(player, dest, onComplete);
        }, warmupSec * 20L);
    }

    private void armCooldown(UUID id) {
        int sec = config.homeTeleportCooldownSeconds();
        if (sec > 0) {
            cooldowns.put(id, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(sec));
        }
    }

    /** Returns true when an active warmup was cancelled by movement. */
    public boolean handleMove(UUID player, Location to) {
        if (!config.homeWarmupCancelOnMove()) return false;
        Location origin = warmupOrigins.get(player);
        if (origin == null || to == null) return false;
        if (origin.getWorld() != null && origin.getWorld().equals(to.getWorld())
                && origin.getBlockX() == to.getBlockX()
                && origin.getBlockY() == to.getBlockY()
                && origin.getBlockZ() == to.getBlockZ()) {
            return false;
        }
        cancelWarmup(player);
        return true;
    }

    /** Drops expired cooldown entries. */
    public void sweepExpired() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(e -> e.getValue() < now);
    }

    public long cooldownRemaining(UUID player) {
        Long end = cooldowns.get(player);
        if (end == null) return 0;
        long rem = end - System.currentTimeMillis();
        return Math.max(0, rem);
    }

    public boolean isWarmingUp(UUID player) {
        return warmups.containsKey(player);
    }

    public void cancelWarmup(UUID player) {
        warmups.remove(player);
        warmupOrigins.remove(player);
    }

    public Result teleport(Player player, Team team, Runnable onWarmupStart,
                           Runnable onComplete, Runnable onCancelled) {
        TeamHome home = team.home();
        if (home == null) return Result.NO_HOME;
        Location dest = home.toLocation();
        if (dest == null) return Result.WORLD_MISSING;

        UUID id = player.getUniqueId();
        if (cooldownRemaining(id) > 0) return Result.ON_COOLDOWN;
        if (warmups.containsKey(id)) return Result.ALREADY_WARMING;

        startTeleport(player, id, dest, onWarmupStart, onComplete, onCancelled);
        return Result.SUCCESS;
    }

    private void doTeleport(Player player, Location dest, Runnable onComplete) {

        player.teleportAsync(dest,
                org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN,
                io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS)
                .thenAccept(success -> {
                    if (success && onComplete != null) {
                        Schedulers.entity(player, onComplete);
                    }
                });
    }

    public void clear(UUID player) {
        cooldowns.remove(player);
        warmups.remove(player);
        warmupOrigins.remove(player);
    }
}
