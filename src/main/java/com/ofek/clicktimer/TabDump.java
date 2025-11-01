package com.ofek.clicktimer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

import static java.nio.file.StandardOpenOption.*;

public final class TabDump {
    public static void dumpTabToFile() {
        var mc = Minecraft.getInstance();
        ClientPacketListener conn = mc.getConnection();
        if (conn == null || mc.level == null) return;

        Scoreboard sb = mc.level.getScoreboard();
        Objective listObj = sb.getDisplayObjective(DisplaySlot.LIST);
        String objName = (listObj == null) ? "(none)" : listObj.getName();

        Collection<PlayerInfo> entries = conn.getOnlinePlayers();

        StringBuilder out = new StringBuilder();
        out.append("===== TAB DUMP ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append(" =====\n");
        out.append("Objective: ").append(objName).append('\n');
        for (PlayerInfo pi : entries) {
            Component tabName = pi.getTabListDisplayName();
            String name = (tabName != null) ? tabName.getString() : pi.getProfile().getName();
            int ping = pi.getLatency();
            var gm = (pi.getGameMode() != null) ? pi.getGameMode().getName() : "unknown";
            out.append(String.format(" - %-20s | ping=%3d | gm=%s%n", name, ping, gm));
        }
        out.append("\n");

        Path dir = mc.gameDirectory.toPath().resolve("tab_dumps");
        Path file = dir.resolve("tab_dump.txt");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, out.toString(), CREATE, WRITE, APPEND);
            mc.player.displayClientMessage(Component.literal("TAB dump saved to tab_dumps/tab_dump.txt"), true);
        } catch (IOException e) {
            mc.player.displayClientMessage(Component.literal("Failed to write TAB dump: " + e.getMessage()), true);
        }
    }
}
