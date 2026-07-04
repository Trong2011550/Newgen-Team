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

    public HomeService(ConfigManager config, StorageProvider storage) {
        this.config = config;
        this.storage = storage;
    }

    public enum Result { SUCCESS, NO_HOME, ON_COOLDOWN, ALREADY_WARMING, WORLD_MISSING }

    public enum SetResult { SUCCESS, LIMIT_REACHED, NAME_TAKEN, INVALID_NAME }

    public void setHome(Team team, Location loc, TeamService teamService) {
        team.home(TeamHome.from(loc));

        team.namedHomes().put("Mặc định", team.home());
        storage.saveTeam(team);
    }

    public SetResult setNamedHome(Team team, String name, Location loc, int homeLimit) {
        if (name == null || name.isBlank() || name.length() > 24) return SetResult.INVALID_NAME;
        boolean exists = team.namedHomes().containsKey(name);
        if (!exists && team.namedHomes().size() >= homeLimit) return SetResult.LIMIT_REACHED;
        team.namedHomes().put(name, TeamHome.from(loc));

        if (team.home() == null) team.home(TeamHome.from(loc));
        storage.saveTeam(team);
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
        storage.saveTeam(team);
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

        int warmupSec = config.homeTeleportWarmupSeconds();
        if (warmupSec <= 0) {
            doTeleport(player, dest, onComplete);
            return Result.SUCCESS;
        }

        long token = System.nanoTime();
        warmups.put(id, token);
        if (onWarmupStart != null) onWarmupStart.run();

        Schedulers.entityLater(player, () -> {
            Long current = warmups.get(id);
            if (current == null || current != token) {
                if (onCancelled != null) onCancelled.run();
                return;
            }
            warmups.remove(id);
            cooldowns.put(id, System.currentTimeMillis()
                    + TimeUnit.SECONDS.toMillis(config.homeTeleportCooldownSeconds()));
            doTeleport(player, dest, onComplete);
        }, warmupSec * 20L);
        return Result.SUCCESS;
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

        int warmupSec = config.homeTeleportWarmupSeconds();
        if (warmupSec <= 0) {
            doTeleport(player, dest, onComplete);
            return Result.SUCCESS;
        }

        long token = System.nanoTime();
        warmups.put(id, token);
        if (onWarmupStart != null) onWarmupStart.run();

        Schedulers.entityLater(player, () -> {
            Long current = warmups.get(id);
            if (current == null || current != token) {
                if (onCancelled != null) onCancelled.run();
                return;
            }
            warmups.remove(id);
            cooldowns.put(id, System.currentTimeMillis()
                    + TimeUnit.SECONDS.toMillis(config.homeTeleportCooldownSeconds()));
            doTeleport(player, dest, onComplete);
        }, warmupSec * 20L);
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
    }
}
