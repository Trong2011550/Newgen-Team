package me.newgen.team.util;

import me.newgen.team.model.TeamHome;
import me.newgen.team.model.TeamSettings;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Json {

    private Json() {}

    public static String writeSettings(TeamSettings s) {
        return "{" +
                "\"open\":" + s.open + "," +
                "\"friendlyFire\":" + s.friendlyFire + "," +
                "\"teamChat\":" + s.teamChat + "," +
                "\"autoAcceptInvite\":" + s.autoAcceptInvite + "," +
                "\"publicJoin\":" + s.publicJoin + "," +
                "\"notifyJoinLeave\":" + s.notifyJoinLeave + "," +
                "\"allyRequests\":" + s.allyRequests +
                "}";
    }

    public static TeamSettings readSettings(String json) {
        TeamSettings s = new TeamSettings();
        if (json == null || json.isEmpty()) return s;
        s.open = bool(json, "open", s.open);
        s.friendlyFire = bool(json, "friendlyFire", s.friendlyFire);
        s.teamChat = bool(json, "teamChat", s.teamChat);
        s.autoAcceptInvite = bool(json, "autoAcceptInvite", s.autoAcceptInvite);
        s.publicJoin = bool(json, "publicJoin", s.publicJoin);
        s.notifyJoinLeave = bool(json, "notifyJoinLeave", s.notifyJoinLeave);
        s.allyRequests = bool(json, "allyRequests", s.allyRequests);
        return s;
    }

    public static String writeHome(TeamHome h) {
        return "{" +
                "\"world\":\"" + escape(h.world) + "\"," +
                "\"x\":" + h.x + ",\"y\":" + h.y + ",\"z\":" + h.z + "," +
                "\"yaw\":" + h.yaw + ",\"pitch\":" + h.pitch +
                "}";
    }

    public static TeamHome readHome(String json) {
        if (json == null || json.isEmpty()) return null;
        String world = str(json, "world");
        double x = num(json, "x");
        double y = num(json, "y");
        double z = num(json, "z");
        float yaw = (float) num(json, "yaw");
        float pitch = (float) num(json, "pitch");
        if (world == null) return null;
        return new TeamHome(world, x, y, z, yaw, pitch);
    }

    public static String writeNamedHomes(Map<String, TeamHome> homes) {
        if (homes == null || homes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, TeamHome> e : homes.entrySet()) {
            TeamHome h = e.getValue();
            if (h == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(clean(e.getKey())).append('\u001f')
              .append(clean(h.world)).append('\u001f')
              .append(h.x).append('\u001f')
              .append(h.y).append('\u001f')
              .append(h.z).append('\u001f')
              .append(h.yaw).append('\u001f')
              .append(h.pitch);
        }
        return sb.toString();
    }

    public static Map<String, TeamHome> readNamedHomes(String data) {
        Map<String, TeamHome> out = new LinkedHashMap<>();
        if (data == null || data.isEmpty()) return out;
        for (String line : data.split("\n")) {
            if (line.isEmpty()) continue;
            String[] f = line.split("\u001f", -1);
            if (f.length != 7) continue;
            try {
                String name = f[0];
                String world = f[1];
                double x = Double.parseDouble(f[2]);
                double y = Double.parseDouble(f[3]);
                double z = Double.parseDouble(f[4]);
                float yaw = Float.parseFloat(f[5]);
                float pitch = Float.parseFloat(f[6]);
                if (!name.isEmpty() && !world.isEmpty()) {
                    out.put(name, new TeamHome(world, x, y, z, yaw, pitch));
                }
            } catch (NumberFormatException ignored) {

            }
        }
        return out;
    }

    /** Strips record/field delimiter characters from user-supplied text. */
    private static String clean(String s) {
        if (s == null) return "";
        return s.replace('\n', ' ').replace('\r', ' ').replace((char) 0x1F, ' ');
    }

    private static boolean bool(String json, String key, boolean def) {
        String k = "\"" + key + "\":";
        int i = json.indexOf(k);
        if (i < 0) return def;
        i += k.length();
        return json.startsWith("true", i);
    }

    private static double num(String json, String key) {
        String k = "\"" + key + "\":";
        int i = json.indexOf(k);
        if (i < 0) return 0;
        i += k.length();
        int j = i;
        while (j < json.length() && "-0123456789.eE".indexOf(json.charAt(j)) >= 0) j++;
        try { return Double.parseDouble(json.substring(i, j)); }
        catch (NumberFormatException e) { return 0; }
    }

    private static String str(String json, String key) {
        String k = "\"" + key + "\":\"";
        int i = json.indexOf(k);
        if (i < 0) return null;
        i += k.length();
        // Find the closing quote, skipping escaped characters.
        int j = i;
        while (j < json.length()) {
            char ch = json.charAt(j);
            if (ch == '\\') {
                j += 2;
                continue;
            }
            if (ch == '"') {
                return unescape(json.substring(i, j));
            }
            j++;
        }
        return null;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
