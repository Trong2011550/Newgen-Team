package me.newgen.team.listener;

import me.newgen.team.NewGenTeamPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Cancels a pending home-teleport warmup when the player leaves the block they
 * started on (enforces homes.yml: home.warmup-cancel-on-move).
 */
public final class MoveListener implements Listener {

    private final NewGenTeamPlugin plugin;

    public MoveListener(NewGenTeamPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        var id = event.getPlayer().getUniqueId();
        if (!plugin.homes().isWarmingUp(id)) return;
        plugin.homes().handleMove(id, event.getTo());
    }
}
