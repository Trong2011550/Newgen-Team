package me.newgen.team.gui;

import me.newgen.team.NewGenTeamPlugin;
import org.bukkit.entity.Player;

public final class MenuManager {

    private final NewGenTeamPlugin plugin;

    public MenuManager(NewGenTeamPlugin plugin) {
        this.plugin = plugin;
    }

    public NewGenTeamPlugin plugin() { return plugin; }

    public void openMain(Player player) {
        new me.newgen.team.gui.menu.MainMenu(plugin, player).open();
    }

    public void openMembers(Player player) {
        new me.newgen.team.gui.menu.MemberMenu(plugin, player, 0).open();
    }

    public void openInvites(Player player) {
        new me.newgen.team.gui.menu.InviteMenu(plugin, player, 0).open();
    }

    public void openMyInvites(Player player) {
        new me.newgen.team.gui.menu.MyInvitesMenu(plugin, player, 0).open();
    }

    public void openSettings(Player player) {
        new me.newgen.team.gui.menu.SettingsMenu(plugin, player).open();
    }

    public void openPermissions(Player player) {
        new me.newgen.team.gui.menu.PermissionMenu(plugin, player, 0).open();
    }

    public void openUpgrades(Player player) {
        new me.newgen.team.gui.menu.UpgradeMenu(plugin, player).open();
    }

    public void openBank(Player player) {
        new me.newgen.team.gui.menu.BankMenu(plugin, player).open();
    }

    public void openHome(Player player) {
        new me.newgen.team.gui.menu.HomeMenu(plugin, player).open();
    }

    public void openStats(Player player) {
        new me.newgen.team.gui.menu.StatsMenu(plugin, player).open();
    }

    public void openTop(Player player) {
        new me.newgen.team.gui.menu.TopMenu(player, plugin,
                me.newgen.team.service.StatsService.TopType.KDR, 0).open();
    }

    public void openRelations(Player player) {
        new me.newgen.team.gui.menu.AllyMenu(plugin, player, 0).open();
    }

    public void openPlayers(Player player) {
        new me.newgen.team.gui.menu.PlayerListMenu(plugin, player, 0).open();
    }

    public void openTeams(Player player) {
        new me.newgen.team.gui.menu.TeamListMenu(plugin, player, 0).open();
    }

    public void openJoinRequests(Player player) {
        new me.newgen.team.gui.menu.JoinRequestMenu(plugin, player, 0).open();
    }
}
