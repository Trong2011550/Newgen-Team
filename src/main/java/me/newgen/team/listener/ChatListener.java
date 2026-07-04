package me.newgen.team.listener;

import me.newgen.team.NewGenTeamPlugin;
import me.newgen.team.service.SignInputService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatListener implements Listener {

    private final NewGenTeamPlugin plugin;

    public ChatListener(NewGenTeamPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (SignInputService.isSearchKeyword(message)) {
            event.viewers().clear();
            event.setCancelled(true);
            plugin.signInput().cancel(event.getPlayer().getUniqueId());
            me.newgen.team.scheduler.Schedulers.entity(event.getPlayer(),
                    () -> new me.newgen.team.gui.menu.SearchMenu(plugin, event.getPlayer()).open());
            return;
        }

        if (!plugin.chat().isToggled(event.getPlayer().getUniqueId())) return;

        event.viewers().clear();
        event.setCancelled(true);

        plugin.chat().sendTeamMessage(event.getPlayer(), message);
    }
}
