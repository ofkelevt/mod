package com.ofek.clicktimer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.nio.file.StandardOpenOption.*;


public final class ClickTimerClient implements ClientModInitializer {
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Path CSV_DIR  = FabricLoader.getInstance().getGameDir().resolve("clicktimer");
    private static final Path CSV_FILE = CSV_DIR.resolve("timings.csv");
    private static volatile boolean csvInit = false;
    private static final class State { long tStart=-1, tFirst=-1; boolean armed=false; }

    private static boolean prevUseDown = false;

@Override
public void onInitializeClient() {
    // arm/disarm each tick
    ClientTickEvents.END_CLIENT_TICK.register(client -> {
        LocalPlayer p = client.player;
        ClientLevel w = client.level;
        if (p == null || w == null) return;
        State st = STATES.computeIfAbsent(p.getUUID(), k -> new State());
        AABB box = new AABB(p.blockPosition()).inflate(64.0);
        boolean found = !w.getEntitiesOfClass(Entity.class, box,
                e -> e != null && e.hasCustomName() && "!!!".equals(e.getCustomName().getString())
        ).isEmpty();

        if (found && !st.armed) {
            st.armed = true; st.tStart = System.currentTimeMillis(); st.tFirst = -1;
            toast("Armed. Waiting for fishing-rod right-click.");
        } else if (!found && st.armed) {
            st.armed = false; st.tStart = -1; st.tFirst = -1;
            toast("Disarmed. \"!!!\" not found.");
        }

        // detect rising edge of the Use key
        boolean down = Minecraft.getInstance().options.keyUse.isDown();
        if (down && !prevUseDown) {
            var stack = p.getMainHandItem();
            if (stack.getItem() == Items.FISHING_ROD) {
                handleRodClick(p);
            }
        }
        prevUseDown = down;
    });
}

    private static void handleRodClick(LocalPlayer p) {
        State st = STATES.computeIfAbsent(p.getUUID(), k -> new State());
        if (!st.armed || st.tStart < 0) return;

        long now = System.currentTimeMillis();
        if (st.tFirst < 0) {
            long dt1 = now - st.tStart; st.tFirst = now;
            toast("t(start→click1) = " + dt1 + " ms"); appendCsv(p, "start_to_first", dt1);
        } else {
            long dt2 = now - st.tFirst;
            toast("t(click1→click2) = " + dt2 + " ms"); appendCsv(p, "first_to_second", dt2);
            st.tStart = now; st.tFirst = -1;
        }
    }

    private static void toast(String msg) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(Component.literal(msg), true);
    }

    private static void appendCsv(LocalPlayer p, String phase, long ms) {
        try {
            if (!csvInit) {
                Files.createDirectories(CSV_DIR);
                if (!Files.exists(CSV_FILE)) Files.writeString(CSV_FILE, "timestamp,player,phase,millis\n", CREATE, WRITE);
                csvInit = true;
            }
            String line = String.format("%d,%s,%s,%d%n", System.currentTimeMillis(), p.getName().getString(), phase, ms);
            Files.writeString(CSV_FILE, line, CREATE, APPEND, WRITE);
        } catch (IOException ex) {
            LocalPlayer me = Minecraft.getInstance().player;
            if (me != null) me.displayClientMessage(Component.literal("timings.csv write failed: " + ex.getMessage()), true);
        }
    }
}
