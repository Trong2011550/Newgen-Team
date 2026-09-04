package me.newgen.team.service;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenSignEditor;
import me.newgen.team.scheduler.Schedulers;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Fake-sign text input driven by packets (no real sign block is placed).
 *
 * Designed for high-population servers: pending sessions live in a
 * ConcurrentHashMap with a per-session timeout that restores the client's
 * block and cancels the callback, so an abandoned sign editor can never leak
 * a session or leave a ghost sign behind.
 */
public final class SignInputService extends PacketListenerAbstract {

    public static final String SEARCH_KEYWORD = "<packevent>";

    private static final String CANCEL_WORD_VI = "huy";
    private static final String CANCEL_WORD_EN = "cancel";

    private static final int SIGN_Y_OFFSET = 3;

    /** Hard cap on accepted sign input; vanilla clients send at most ~90 chars/line. */
    private static final int MAX_INPUT_LENGTH = 64;

    /** Seconds before an unanswered sign editor is cancelled and its fake block restored. */
    private static final int TIMEOUT_SECONDS = 60;

    private record Pending(Consumer<String> callback, Vector3i pos, Location restore) {}

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    /** GlobalId of a standing OAK_SIGN, computed once instead of per open. */
    private static volatile int cachedSignGlobalId = -1;

    public SignInputService() {
        super(PacketListenerPriority.NORMAL);
    }

    private static int signGlobalId() {
        int id = cachedSignGlobalId;
        if (id == -1) {
            id = SpigotConversionUtil.fromBukkitBlockData(Material.OAK_SIGN.createBlockData()).getGlobalId();
            cachedSignGlobalId = id;
        }
        return id;
    }

    public void await(Player player, Consumer<String> onInput) {
        UUID id = player.getUniqueId();
        cancel(id);

        Location base = player.getLocation();
        int x = base.getBlockX();
        int y = Math.max(base.getWorld().getMinHeight(), base.getBlockY() - SIGN_Y_OFFSET);
        int z = base.getBlockZ();
        Vector3i pos = new Vector3i(x, y, z);

        Location restoreLoc = new Location(base.getWorld(), x, y, z);
        Pending session = new Pending(onInput, pos, restoreLoc);
        pending.put(id, session);

        // Timeout: if the client never sends UPDATE_SIGN (lag, packet loss,
        // editor left open), drop the session and restore the real block.
        Schedulers.entityLater(player, () -> {
            if (pending.remove(id, session)) {
                sendRestore(player, pos);
                Schedulers.entity(player, () -> onInput.accept(null));
            }
        }, TIMEOUT_SECONDS * 20L);

        Schedulers.entity(player, () -> {
            if (pending.get(id) != session) return;

            WrapperPlayServerBlockChange fake = new WrapperPlayServerBlockChange(pos, signGlobalId());
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, fake);

            WrapperPlayServerOpenSignEditor open = new WrapperPlayServerOpenSignEditor(pos, true);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, open);
        });
    }

    public void cancel(UUID player) {
        Pending p = pending.remove(player);
        if (p != null) {
            Player bukkit = org.bukkit.Bukkit.getPlayer(player);
            // Only restore while the entity is still trackable (online);
            // on quit the client-side sign disappears on its own.
            if (bukkit != null && bukkit.isOnline()) {
                sendRestore(bukkit, p.pos());
            }
        }
    }

    public boolean isAwaiting(UUID player) {
        return pending.containsKey(player);
    }

    private static void sendRestore(Player player, Vector3i pos) {
        Schedulers.entity(player, () -> {
            if (!player.isOnline()) return;
            BlockData real = pos == null ? null : player.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()).getBlockData();
            if (real == null) return;
            WrapperPlayServerBlockChange restore = new WrapperPlayServerBlockChange(
                    pos, SpigotConversionUtil.fromBukkitBlockData(real).getGlobalId());
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, restore);
        });
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.UPDATE_SIGN) return;

        UUID id = event.getUser().getUUID();
        if (id == null) return;

        // Fast path: the vast majority of sign updates on a busy server have
        // no pending session; nothing is parsed or allocated for them.
        Pending p = pending.remove(id);
        if (p == null) return;

        Player player = (Player) event.getPlayer();
        if (player == null) return;

        WrapperPlayClientUpdateSign wrapper = new WrapperPlayClientUpdateSign(event);
        String[] lines = wrapper.getTextLines();
        String input = lines.length > 0 && lines[0] != null ? sanitize(lines[0]) : "";

        sendRestore(player, p.pos());

        Schedulers.entity(player, () -> {
            if (input.isEmpty()
                    || input.equalsIgnoreCase(CANCEL_WORD_VI)
                    || input.equalsIgnoreCase(CANCEL_WORD_EN)) {
                p.callback().accept(null);
            } else {
                p.callback().accept(input);
            }
        });
    }

    /** Strips colour codes, control characters and over-length input. */
    private static String sanitize(String raw) {
        if (raw == null) return "";
        String s = raw.replace("§", "");
        StringBuilder sb = new StringBuilder(Math.min(s.length(), MAX_INPUT_LENGTH));
        for (int i = 0; i < s.length() && sb.length() < MAX_INPUT_LENGTH; i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c != 0x7F) sb.append(c);
        }
        return sb.toString().trim();
    }

    public static boolean isSearchKeyword(String message) {
        return message != null && message.trim().equalsIgnoreCase(SEARCH_KEYWORD);
    }
}
