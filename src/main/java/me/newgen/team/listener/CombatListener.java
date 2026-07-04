package me.newgen.team.listener;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.model.Team;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class CombatListener implements Listener {

    private final NewGenTeamPlugin plugin;

    public CombatListener(NewGenTeamPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.equals(victim)) return;

        Team vTeam = plugin.teams().byPlayer(victim.getUniqueId());
        Team aTeam = plugin.teams().byPlayer(attacker.getUniqueId());
        if (vTeam == null || aTeam == null) return;

        if (vTeam.id().equals(aTeam.id())) {
            if (!vTeam.settings().friendlyFire) event.setCancelled(true);
            return;
        }

        if (aTeam.relations().get(vTeam.id()) == Team.RelationType.ALLY
                || vTeam.relations().get(aTeam.id()) == Team.RelationType.ALLY) {
            event.setCancelled(true);
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.config().trackKills()) return;
        Player victim = event.getEntity();
        plugin.stats().addDeath(victim.getUniqueId());
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            Team kTeam = plugin.teams().byPlayer(killer.getUniqueId());
            Team vTeam = plugin.teams().byPlayer(victim.getUniqueId());

            if (kTeam != null && (vTeam == null || !kTeam.id().equals(vTeam.id()))) {
                plugin.stats().addKill(killer.getUniqueId());
            }
        }
    }
}
