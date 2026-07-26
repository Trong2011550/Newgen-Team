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

        // The keyword is only meaningful during an active sign-input session.
        if (SignInputService.isSearchKeyword(message)
                && plugin.signInput().isAwaiting(event.getPlayer().getUniqueId())) {
            hide(event);
            plugin.signInput().cancel(event.getPlayer().getUniqueId());
            me.newgen.team.scheduler.Schedulers.entity(event.getPlayer(),
                    () -> new me.newgen.team.gui.menu.SearchMenu(plugin, event.getPlayer()).open());
            return;
        }

        java.util.UUID id = event.getPlayer().getUniqueId();
        if (!plugin.chat().isToggled(id)) return;

        hide(event);

        if (!plugin.chat().sendTeamMessage(event.getPlayer(), message)) {
            // No team anymore (kicked/disbanded): drop the message privately.
            plugin.chat().clear(id);
            plugin.messages().send(event.getPlayer(), "team.not-in");
        }
    }

    /** Cancels before touching viewers(); the viewer set may be immutable. */
    private static void hide(AsyncChatEvent event) {
        event.setCancelled(true);
        try {
            event.viewers().clear();
        } catch (UnsupportedOperationException ignored) {
        }
    }
}
