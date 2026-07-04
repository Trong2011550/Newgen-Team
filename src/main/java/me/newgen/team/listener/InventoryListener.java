package me.newgen.team.listener;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Menu;
import me.newgen.team.gui.MenuHolder;
import me.newgen.team.model.Team;
import me.newgen.team.permission.TeamPermission;
import me.newgen.team.service.ChestService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public final class InventoryListener implements Listener {

    private final NewGenTeamPlugin plugin;

    public InventoryListener(NewGenTeamPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof MenuHolder mh) {
            event.setCancelled(true);
            Menu menu = mh.menu();
            if (menu != null && !menu.allowItemMovement()) {
                menu.handleClick(event);
            }
            return;
        }

        if (holder instanceof ChestService.ChestHolder ch) {
            if (!(event.getWhoClicked() instanceof Player player)) { event.setCancelled(true); return; }
            Team team = plugin.teams().byId(ch.teamId());
            if (team == null) { event.setCancelled(true); return; }
            if (handleNavigation(event, player, team, ch)) return;
            enforceChestPermissions(event, player, team);
        }
    }

    private boolean handleNavigation(InventoryClickEvent event, Player player, Team team, ChestService.ChestHolder ch) {
        boolean topClicked = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());
        int slot = event.getSlot();

        if (topClicked && slot >= ChestService.NAV_ROW_START) {
            event.setCancelled(true);
            if (slot == ChestService.BACK_SLOT) {
                me.newgen.team.util.Sounds.click(player);
                plugin.menus().openMain(player);
            } else if (slot == ChestService.PREV_SLOT && ch.page() > 0) {
                me.newgen.team.util.Sounds.page(player);
                plugin.chests().open(player, team, ch.page() - 1);
            } else if (slot == ChestService.NEXT_SLOT && ch.page() < plugin.chests().pages(team) - 1) {
                me.newgen.team.util.Sounds.page(player);
                plugin.chests().open(player, team, ch.page() + 1);
            }
            return true;
        }

        if (!topClicked && event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            org.bukkit.inventory.ItemStack moving = event.getCurrentItem();
            if (moving != null && !moving.getType().isAir()) {
                org.bukkit.inventory.Inventory top = event.getView().getTopInventory();
                if (!canAbsorbInStorage(top, moving)) {

                    event.setCancelled(true);
                    me.newgen.team.util.Sounds.error(player);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canAbsorbInStorage(org.bukkit.inventory.Inventory top, org.bukkit.inventory.ItemStack moving) {
        for (int i = 0; i < ChestService.NAV_ROW_START; i++) {
            org.bukkit.inventory.ItemStack it = top.getItem(i);
            if (it == null || it.getType().isAir()) return true;
            if (it.getAmount() < it.getMaxStackSize() && it.isSimilar(moving)) return true;
        }
        return false;
    }

    private void enforceChestPermissions(InventoryClickEvent event, Player player, Team team) {
        boolean canWithdraw = plugin.permissions().has(team, player.getUniqueId(), TeamPermission.CHEST_WITHDRAW);
        boolean canDeposit = plugin.permissions().has(team, player.getUniqueId(), TeamPermission.CHEST_DEPOSIT);

        boolean topClicked = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());

        switch (event.getAction()) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR -> {
                if (topClicked && !canDeposit) { deny(event, player); }
            }
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME -> {
                if (topClicked && !canWithdraw) { deny(event, player); }
            }
            case MOVE_TO_OTHER_INVENTORY -> {

                if (topClicked && !canWithdraw) deny(event, player);
                else if (!topClicked && !canDeposit) deny(event, player);
            }
            case COLLECT_TO_CURSOR -> {
                if (!canWithdraw) deny(event, player);
            }
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                if (topClicked && !(canWithdraw && canDeposit)) deny(event, player);
            }
            default -> {}
        }
    }

    private void deny(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        me.newgen.team.util.Sounds.error(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MenuHolder) {
            event.setCancelled(true);
            return;
        }
        if (holder instanceof ChestService.ChestHolder ch && event.getWhoClicked() instanceof Player player) {
            Team team = plugin.teams().byId(ch.teamId());
            if (team == null) { event.setCancelled(true); return; }
            int topSize = event.getView().getTopInventory().getSize();

            boolean touchesNav = event.getRawSlots().stream()
                    .anyMatch(s -> s < topSize && s >= ChestService.NAV_ROW_START);
            if (touchesNav) {
                event.setCancelled(true);
                me.newgen.team.util.Sounds.error(player);
                return;
            }
            boolean touchesTop = event.getRawSlots().stream().anyMatch(s -> s < topSize);
            if (touchesTop && !plugin.permissions().has(team, player.getUniqueId(), TeamPermission.CHEST_DEPOSIT)) {
                event.setCancelled(true);
                me.newgen.team.util.Sounds.error(player);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MenuHolder mh) {
            if (mh.menu() != null) mh.menu().onClose();
            return;
        }
        if (holder instanceof ChestService.ChestHolder ch) {
            Team team = plugin.teams().byId(ch.teamId());
            if (team != null) plugin.chests().onClose(team);
        }
    }
}
