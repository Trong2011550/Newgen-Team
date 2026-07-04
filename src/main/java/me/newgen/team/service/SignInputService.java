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

public final class SignInputService extends PacketListenerAbstract {

    public static final String SEARCH_KEYWORD = "<packevent>";

    private static final String CANCEL_WORD_VI = "huy";
    private static final String CANCEL_WORD_EN = "cancel";

    private static final int SIGN_Y_OFFSET = 3;

    private record Pending(Consumer<String> callback, Vector3i pos, Location restore) {}

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public SignInputService() {
        super(PacketListenerPriority.NORMAL);
    }

    public void await(Player player, Consumer<String> onInput) {

        cancel(player.getUniqueId());

        Location base = player.getLocation();
        int x = base.getBlockX();
        int y = Math.max(base.getWorld().getMinHeight(), base.getBlockY() - SIGN_Y_OFFSET);
        int z = base.getBlockZ();
        Vector3i pos = new Vector3i(x, y, z);

        Location restoreLoc = new Location(base.getWorld(), x, y, z);
        pending.put(player.getUniqueId(), new Pending(onInput, pos, restoreLoc));

        Schedulers.entity(player, () -> {

            WrapperPlayServerBlockChange fake = new WrapperPlayServerBlockChange(
                    pos, SpigotConversionUtil.fromBukkitBlockData(Material.OAK_SIGN.createBlockData()).getGlobalId());
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, fake);

            WrapperPlayServerOpenSignEditor open = new WrapperPlayServerOpenSignEditor(pos, true);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, open);
        });
    }

    public void cancel(UUID player) {
        pending.remove(player);
    }

    public boolean isAwaiting(UUID player) {
        return pending.containsKey(player);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.UPDATE_SIGN) return;

        UUID id = event.getUser().getUUID();
        if (id == null) return;
        Pending p = pending.remove(id);
        if (p == null) return;

        WrapperPlayClientUpdateSign wrapper = new WrapperPlayClientUpdateSign(event);
        String[] lines = wrapper.getTextLines();
        String input = lines.length > 0 && lines[0] != null ? lines[0].trim() : "";

        Player player = (Player) event.getPlayer();
        if (player == null) return;

        Schedulers.entity(player, () -> {
            BlockData real = p.restore().getBlock().getBlockData();
            WrapperPlayServerBlockChange restore = new WrapperPlayServerBlockChange(
                    p.pos(), SpigotConversionUtil.fromBukkitBlockData(real).getGlobalId());
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, restore);

            if (input.isEmpty()
                    || input.equalsIgnoreCase(CANCEL_WORD_VI)
                    || input.equalsIgnoreCase(CANCEL_WORD_EN)) {
                p.callback().accept(null);
            } else {
                p.callback().accept(input);
            }
        });
    }

    public static boolean isSearchKeyword(String message) {
        return message != null && message.trim().equalsIgnoreCase(SEARCH_KEYWORD);
    }
}
