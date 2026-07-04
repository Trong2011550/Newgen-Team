package me.newgen.team.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;

public final class Text {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder()
                    .character('&')
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Text() {}

    public static Component legacy(String input) {
        if (input == null) return Component.empty();
        return LEGACY.deserialize(input).decorationIfAbsent(
                net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }

    /**
     * Transitional renderer: parses MiniMessage when the string contains
     * MiniMessage-style tags, otherwise falls back to legacy & codes.
     * Lets themed/lang strings and old hardcoded GUI strings coexist
     * until the GUI phase migrates everything to MiniMessage.
     */
    public static Component auto(String input) {
        if (input == null) return Component.empty();
        return input.indexOf('<') >= 0 ? mini(input) : legacy(input);
    }

    public static List<Component> autoList(List<String> lines) {
        return lines.stream().map(Text::auto).toList();
    }

    public static Component mini(String input) {
        if (input == null) return Component.empty();
        return MINI.deserialize(input).decorationIfAbsent(
                net.kyori.adventure.text.format.TextDecoration.ITALIC,
                net.kyori.adventure.text.format.TextDecoration.State.FALSE);
    }

    public static List<Component> legacyList(List<String> lines) {
        return lines.stream().map(Text::legacy).toList();
    }

    public static Component galaxy(String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        int[][] stops = {
                {0x5B, 0x2C, 0x83},
                {0xC4, 0x3C, 0xC9},
                {0xF7, 0x9A, 0xD3},
                {0x57, 0xE3, 0xF0}
        };
        StringBuilder mm = new StringBuilder();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            double t = len == 1 ? 0.0 : (double) i / (len - 1);
            int seg = (int) Math.floor(t * (stops.length - 1));
            if (seg >= stops.length - 1) seg = stops.length - 2;
            double local = t * (stops.length - 1) - seg;
            int r = (int) Math.round(stops[seg][0] + (stops[seg + 1][0] - stops[seg][0]) * local);
            int g = (int) Math.round(stops[seg][1] + (stops[seg + 1][1] - stops[seg][1]) * local);
            int b = (int) Math.round(stops[seg][2] + (stops[seg + 1][2] - stops[seg][2]) * local);
            String hex = String.format("#%02X%02X%02X", r, g, b);
            mm.append("<").append(hex).append(">");

            if (c == '<') mm.append("\\<");
            else mm.append(c);
        }
        return mini(mm.toString());
    }

    public static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(component);
    }
}
