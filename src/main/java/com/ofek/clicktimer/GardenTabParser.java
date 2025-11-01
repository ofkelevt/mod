package com.ofek.clicktimer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GardenTabParser {

    public static final class Result {
        public final boolean areaIsGarden;
        public final String repellent; // "" if missing
        public final int alive;        // -1 if missing

        public Result(boolean areaIsGarden, String repellent, int alive) {
            this.areaIsGarden = areaIsGarden;
            this.repellent = repellent;
            this.alive = alive;
        }
    }

    private static final Pattern ALIVE_RX = Pattern.compile("\\bAlive:\\s*(\\d+)\\b");

    public static Result readGardenStatus() {
        var mc = Minecraft.getInstance();
        ClientPacketListener conn = mc.getConnection();
        if (conn == null || mc.level == null) return new Result(false, "", -1);

        // Build TAB lines in the same shape as your dump:
        // " - <label> | ping=<n> | gm=<mode>"
        List<String> lines = new ArrayList<>();
        Scoreboard sb = mc.level.getScoreboard();
        Objective listObj = sb.getDisplayObjective(DisplaySlot.LIST);
        // objective unused by the parser, kept for parity if you extend later

        for (PlayerInfo pi : conn.getOnlinePlayers()) {
            Component tabName = pi.getTabListDisplayName();
            String label = (tabName != null) ? tabName.getString() : ""; // empty cells exist
            int ping = pi.getLatency();
            String gm = (pi.getGameMode() != null) ? pi.getGameMode().getName() : "unknown";
            lines.add(String.format(Locale.ROOT, " - %s | ping=%3d | gm=%s", label, ping, gm));
        }

        // Filter only lines that start with " - " and contain the ping/gm suffix (like your dump)
        List<String> filtered = new ArrayList<>();
        for (String s : lines) {
            if (s.startsWith(" - ") && s.contains(" | ping=") && s.contains(" | gm=")) {
                filtered.add(s);
            }
        }

        boolean areaIsGarden = false;
        String repellent = "";
        int alive = -1;

        // Step 1: find any line that includes "Area"
        // Step 2: if equals "Area: Garden" → unlock the rest
        for (String s : filtered) {
            String core = coreLabel(s); // strip prefix and suffix to get the label part
            if (core.contains("Area")) {
                if (core.equals("Area: Garden")) {
                    areaIsGarden = true;
                }
                break; // we only check existence and possibly equality against Garden
            }
        }

        if (areaIsGarden) {
            // Find Repellent: <rest>
            for (String s : filtered) {
                String core = coreLabel(s);
                int i = core.indexOf("Repellent:");
                if (i >= 0) {
                    repellent = core.substring(i + "Repellent:".length()).trim();
                    break;
                }
            }
            // Find Alive: <number>
            for (String s : filtered) {
                String core = coreLabel(s);
                Matcher m = ALIVE_RX.matcher(core);
                if (m.find()) {
                    try {
                        alive = Integer.parseInt(m.group(1));
                    } catch (NumberFormatException ignored) {}
                    break;
                }
            }
        }

        return new Result(areaIsGarden, repellent, alive);
    }

    // Extract the label between " - " and " | ping=..."
    private static String coreLabel(String line) {
        int start = 3; // after " - "
        int cut = line.indexOf(" | ping=");
        if (cut < 0) cut = line.length();
        String core = line.substring(start, Math.max(start, cut)).trim();
        return core;
    }
}
