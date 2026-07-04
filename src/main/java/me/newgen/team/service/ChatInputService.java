package me.newgen.team.service;

import me.newgen.team.scheduler.Schedulers;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ChatInputService {

    public static final String SEARCH_KEYWORD = "<packevent>";

    private static final String CANCEL_WORD_VI = "huy";
    private static final String CANCEL_WORD_EN = "cancel";

    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public void await(Player player, Consumer<String> onInput) {
        pending.put(player.getUniqueId(), onInput);
    }

    public void cancel(UUID player) {
        pending.remove(player);
    }

    public boolean isAwaiting(UUID player) {
        return pending.containsKey(player);
    }

    public boolean offer(Player player, String message) {
        Consumer<String> callback = pending.remove(player.getUniqueId());
        if (callback == null) return false;

        String trimmed = message.trim();
        if (trimmed.equalsIgnoreCase(CANCEL_WORD_VI) || trimmed.equalsIgnoreCase(CANCEL_WORD_EN)) {
            Schedulers.entity(player, () -> callback.accept(null));
            return true;
        }
        Schedulers.entity(player, () -> callback.accept(trimmed));
        return true;
    }

    public static boolean isSearchKeyword(String message) {
        return message != null && message.trim().equalsIgnoreCase(SEARCH_KEYWORD);
    }
}
