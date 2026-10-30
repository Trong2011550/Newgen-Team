package me.newgen.team.service;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.newgen.team.config.ConfigManager;
import me.newgen.team.config.MessageManager;
import me.newgen.team.scheduler.Schedulers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Modern text input using the Paper Dialog API, with per-client version routing.
 *
 * Dialogs are the native replacement for the fake-sign hack: no ghost block, no
 * packet restore, works while any inventory is open, and the client renders a
 * proper titled prompt. The Dialog *packet* only exists on Minecraft 1.21.6+, so
 * a client below that (e.g. a ViaVersion 1.20 player on a 26.x server) simply
 * would not render it - we detect each player's real protocol via PacketEvents and
 * hand those sessions to {@link SignInputService}'s fake-sign editor instead.
 *
 * The overall behaviour is governed by settings.yml {@code gui.input-method}:
 * AUTO (smart per client), DIALOG (force), SIGN (never use dialogs).
 *
 * Each prompt is a one-shot session guarded by an {@link AtomicBoolean} so the
 * submit callback and the timeout can never both fire, and an abandoned dialog
 * (closed with escape) resolves to a null/cancel via the timeout.
 */
public final class DialogInputService {

    private static final String INPUT_KEY = "newgen_input";
    private static final int MAX_LENGTH = 64;
    private static final int TIMEOUT_SECONDS = 120;

    /** True only when both the Paper Dialog API and Adventure DialogLike are present. */
    private static final boolean SUPPORTED = detect();

    /** Lowest client protocol that understands the dialog packet. */
    private static final ClientVersion DIALOG_MIN = ClientVersion.V_1_21_6;

    private static boolean detect() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            Class<?> like = Class.forName("net.kyori.adventure.dialog.DialogLike");
            net.kyori.adventure.audience.Audience.class.getMethod("showDialog", like);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private record Session(Consumer<String> callback, AtomicBoolean answered) {}

    private final Map<UUID, Session> pending = new ConcurrentHashMap<>();
    private final MessageManager messages;
    private final ConfigManager config;

    public DialogInputService(MessageManager messages, ConfigManager config) {
        this.messages = messages;
        this.config = config;
    }

    public boolean supported() {
        return SUPPORTED;
    }

    /**
     * Whether a dialog should be used for this player right now, honouring the
     * configured strategy and the player's actual client protocol version.
     */
    public boolean availableFor(Player player) {
        if (!SUPPORTED) return false;
        ConfigManager.InputMethod method = config.inputMethod();
        switch (method) {
            case SIGN:
                return false;
            case DIALOG:
                // Owner forced dialogs; trust them even on old clients.
                return true;
            case AUTO:
            default:
                return clientSupportsDialog(player);
        }
    }

    private boolean clientSupportsDialog(Player player) {
        if (player == null) return false;
        try {
            ClientVersion cv = PacketEvents.getAPI().getPlayerManager().getClientVersion(player);
            // Unknown/unmapped client version -> fall back to the sign editor,
            // which works on every protocol. Never leave a player with no input.
            if (cv == null) return false;
            return cv.isNewerThanOrEquals(DIALOG_MIN);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Shows a single-line text dialog if one is appropriate for this player.
     * Returns true when a dialog was presented (callback fires later); false when
     * the caller should fall back to another input method.
     */
    public boolean promptText(Player player, Consumer<String> onInput) {
        if (!availableFor(player)) return false;
        return promptText(player,
                messages.component("gui.dialog.title"),
                messages.component("gui.dialog.label"),
                MAX_LENGTH, null, onInput);
    }

    public boolean promptText(Player player, Component title, Component label,
                              int maxLength, String initial, Consumer<String> onInput) {
        if (player == null || !player.isOnline()) return false;
        if (!availableFor(player)) return false;

        UUID id = player.getUniqueId();
        cancel(id);

        AtomicBoolean answered = new AtomicBoolean(false);
        Session session = new Session(onInput, answered);
        pending.put(id, session);

        DialogActionCallback click = (view, audience) -> {
            if (!answered.compareAndSet(false, true)) return;
            pending.remove(id, session);
            String value = sanitize(view == null ? null : view.getText(INPUT_KEY));
            Schedulers.entity(player, () -> onInput.accept(value.isEmpty() ? null : value));
        };

        Dialog dialog;
        try {
            TextDialogInput input = DialogInput.text(INPUT_KEY, label)
                    .maxLength(maxLength)
                    .initial(initial == null ? "" : initial)
                    .build();

            ActionButton submit = ActionButton.builder(messages.component("gui.dialog.submit"))
                    .action(DialogAction.customClick(click, ClickCallback.Options.builder()
                            .uses(1)
                            .lifetime(Duration.ofSeconds(TIMEOUT_SECONDS))
                            .build()))
                    .build();

            List<DialogBody> body = new ArrayList<>(2);
            body.add(DialogBody.item(new ItemStack(Material.WRITABLE_BOOK))
                    .showDecorations(true)
                    .build());
            Component desc = messages.component("gui.dialog.desc");
            if (desc != null) body.add(DialogBody.plainMessage(desc));

            dialog = Dialog.create(factory -> factory.empty()
                    .base(DialogBase.builder(title)
                            .canCloseWithEscape(true)
                            .afterAction(DialogBase.DialogAfterAction.CLOSE)
                            .body(body)
                            .inputs(List.of(input))
                            .build())
                    .type(DialogType.multiAction(List.of(submit)).build()));
        } catch (Throwable ex) {
            // API present but dialog construction failed (e.g. registry not ready):
            // release the session and let the caller fall back to the sign editor.
            pending.remove(id, session);
            answered.set(true);
            return false;
        }

        final Dialog shown = dialog;
        Schedulers.entity(player, () -> {
            if (player.isOnline()) {
                try {
                    player.showDialog(shown);
                } catch (Throwable ex) {
                    if (answered.compareAndSet(false, true)) {
                        pending.remove(id, session);
                        onInput.accept(null);
                    }
                }
            }
        });

        // Escape/abandon path: nothing clicked within the timeout resolves as cancel.
        Schedulers.entityLater(player, () -> {
            if (answered.compareAndSet(false, true)) {
                pending.remove(id, session);
                Schedulers.entity(player, () -> onInput.accept(null));
            }
        }, TIMEOUT_SECONDS * 20L);

        return true;
    }

    public void cancel(UUID id) {
        Session s = pending.remove(id);
        if (s != null) s.answered().set(true);
    }

    public boolean isAwaiting(UUID id) {
        return pending.containsKey(id);
    }

    /** Strips colour codes, control characters and over-length input (parity with the sign path). */
    private static String sanitize(String raw) {
        if (raw == null) return "";
        String s = raw.replace("§", "");
        StringBuilder sb = new StringBuilder(Math.min(s.length(), MAX_LENGTH));
        for (int i = 0; i < s.length() && sb.length() < MAX_LENGTH; i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c != 0x7F) sb.append(c);
        }
        return sb.toString().trim();
    }
}
