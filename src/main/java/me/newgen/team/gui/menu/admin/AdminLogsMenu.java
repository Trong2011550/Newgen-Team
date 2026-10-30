package me.newgen.team.gui.menu.admin;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.gui.Icons;
import me.newgen.team.gui.PaginatedMenu;
import me.newgen.team.model.AuditLog;
import me.newgen.team.scheduler.Schedulers;
import me.newgen.team.util.ItemBuilder;
import me.newgen.team.util.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AdminLogsMenu extends PaginatedMenu<AuditLog> {

    // DateTimeFormatter is thread-safe (SimpleDateFormat is not).
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault());

    private final NewGenTeamPlugin plugin;
    private final UUID teamId;
    // Written from the storage thread's callback, read on the main thread during build.
    private volatile List<AuditLog> loaded;

    public AdminLogsMenu(NewGenTeamPlugin plugin, Player viewer, UUID teamId, int page) {
        super(viewer, page);
        this.plugin = plugin;
        this.teamId = teamId;
    }

    private String L(String key, Object... pairs) {
        return plugin.messages().format(key, pairs);
    }

    @Override protected String title() { return L("gui.admin.logs.title"); }
    @Override protected int pageSize() { return 45; }

    @Override
    public void open() {
        if (loaded == null) {
            plugin.auditLogs().history(teamId).thenAccept(list -> {
                List<AuditLog> safe = list == null ? new ArrayList<>() : new ArrayList<>(list);
                Schedulers.entity(viewer, () -> {
                    this.loaded = safe;
                    super.open();
                });
            }).exceptionally(ex -> {
                plugin.logs().error("Failed to load audit logs for " + teamId + ": " + ex.getMessage());
                Schedulers.entity(viewer, () -> {
                    this.loaded = new ArrayList<>();
                    super.open();
                });
                return null;
            });
            return;
        }
        super.open();
    }

    @Override
    protected List<AuditLog> elements() {
        return loaded == null ? List.of() : loaded;
    }

    @Override
    protected ItemStack render(AuditLog log) {
        List<String> lore = new ArrayList<>();
        lore.add(L("gui.admin.logs.time-line", "time", DATE.format(Instant.ofEpochMilli(log.timestamp()))));
        lore.add(L("gui.admin.logs.actor-line", "actor",
                log.actorName() == null ? L("gui.admin.logs.actor-system") : log.actorName()));
        if (log.targetName() != null) {
            lore.add(L("gui.admin.logs.target-line", "target", log.targetName()));
        }
        if (log.extra() != null && !log.extra().isBlank()) {
            lore.add(L("gui.admin.logs.detail-line", "detail", log.extra()));
        }
        return ItemBuilder.of(log.action().isAdmin() ? Material.WRITABLE_BOOK : Material.PAPER)
                .name(L("gui.admin.logs.entry-name", "action", log.action().display()))
                .lore(lore).build();
    }

    @Override
    protected void onElementClick(AuditLog element, InventoryClickEvent event) {

    }

    @Override
    protected void onBack() { new AdminTeamDetailMenu(plugin, viewer, teamId).open(); }

    @Override
    protected void decorate() {
        for (int i = 45; i < 54; i++) set(i, Icons.edgeFiller());
        if (loaded != null && loaded.isEmpty()) {
            set(22, ItemBuilder.of(Material.GRAY_DYE)
                    .name(L("gui.admin.logs.empty"))
                    .lore(L("gui.admin.logs.empty-lore")).build());
        }
    }
}
