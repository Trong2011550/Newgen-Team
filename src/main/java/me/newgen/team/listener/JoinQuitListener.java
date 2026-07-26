package me.newgen.team.listener;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.model.Team;
import me.newgen.team.model.TeamMember;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class JoinQuitListener implements Listener {

    private final NewGenTeamPlugin plugin;

    public JoinQuitListener(NewGenTeamPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Team team = plugin.teams().byPlayer(event.getPlayer().getUniqueId());
        if (team == null) return;
        TeamMember m = team.member(event.getPlayer().getUniqueId());
        if (m != null && !event.getPlayer().getName().equals(m.name())) {
            m.name(event.getPlayer().getName());
            plugin.teams().persist(team);
        }
        if (team.settings().notifyJoinLeave) {
            notifyTeam(team, event.getPlayer().getUniqueId(), "notify.member-online",
                    event.getPlayer().getName());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var id = event.getPlayer().getUniqueId();
        plugin.chat().clear(id);
        plugin.signInput().cancel(id);
        plugin.homes().cancelWarmup(id);
        Team team = plugin.teams().byPlayer(id);
        if (team != null && team.settings().notifyJoinLeave) {
            notifyTeam(team, id, "notify.member-offline", event.getPlayer().getName());
        }
    }

    private void notifyTeam(Team team, java.util.UUID except, String key, String playerName) {
        for (TeamMember m : team.members()) {
            if (m.uuid().equals(except)) continue;
            var p = org.bukkit.Bukkit.getPlayer(m.uuid());
            if (p != null && p.isOnline()) {
                plugin.messages().send(p, key, "player", playerName);
            }
        }
    }
}
