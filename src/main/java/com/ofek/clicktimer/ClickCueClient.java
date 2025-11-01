package com.ofek.clicktimer;

import com.ofek.nativeinput.NativeInput;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.*;

import com.sun.jna.*;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.win32.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import com.ofek.clicktimer.TabDump;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ClickCueClient implements ClientModInitializer {

    private static final Path CSV = FabricLoader.getInstance().getGameDir().resolve("clicktimer").resolve("timings.csv");
    private static final Logger LOG = LoggerFactory.getLogger("ClickCue");
    private static KeyMapping openConfigKey;
    // state
    private static boolean enabled = false;
    private static boolean armed = false;
    private static long tFirstCue = -1L;
    private static long tSecondCue = -1L;
    private static boolean firstBuzzed = true;
    private static boolean secondBuzzed = true;
    
    // HUD
    private static volatile String hudMsg = "";
    private static volatile int hudTicks = 0;
    private static volatile boolean killMobs = false;

    // averages (ms)
    private static double avgFirst = 500.0;
    private static double avgSecond = 300.0;

    private static KeyMapping toggleKey;
    private static boolean errorLogged = false;

    private static long tPostCheck = -1L;
    private static boolean postChecked = false;
    private static long lastKillTime = 0L;
    private static boolean over = false;
    private static boolean failed = false;
    private static boolean first = true;
    private static boolean second = false;
    private static boolean  midkill = false;
    private static boolean goldenFish = false;
    private static long firstDelay = 0L;
    private static long secondDelay = 0L;
    private static long cpsDelay = 0L;
    private static long check = 0L;
    private static long goldenFishCheck = -1L;
    private static boolean last = false;
    private static long[] firstArray;
    private static long[] secondArray;
    public static float timeUntilFish = 0.0f;
    public static long waitForSlug = 0L;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("dumpTab").executes(ctx -> {
                TabDump.dumpTabToFile();
                return 1;
            }));
        });
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.clicktimer.toggle", GLFW.GLFW_KEY_K, "key.categories.misc"));
        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.clicktimer.config", GLFW.GLFW_KEY_O, "key.categories.misc"));
        Farming.init();
        // draw HUD
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            if (killMobs) {
                drawCentered(graphics, "KILL MOBS", 0xFFFF0000, 3.0f);
                return;
            }
            if (hudTicks > 0 && !hudMsg.isEmpty()) {
                drawCentered(graphics, hudMsg, 0xFFFFFF00, 1.6f);
            }
            if (errorLogged) {
                drawCentered(graphics, "ERROR: See log", 0xFF000000, 1.6f);
                errorLogged = false;
            }
            if((Farming.activeAuto && !Farming.stopped) || Farming.activeMenual){
                drawCentered(graphics, "WORKING", 0x00800000, 4f);
            } 
        });


        // tick logic
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;
            while (openConfigKey.consumeClick()) {
            client.setScreen(new TimeUntilFishScreen());
            }
            // toggle
            while (toggleKey.consumeClick()) {
                enabled = !enabled;
                client.player.displayClientMessage(Component.literal("ClickTimer: " + (enabled ? "ENABLED" : "DISABLED")), true);
                if (!enabled) reset();
                else loadAverages();
            }
            if (!enabled) return;

            final LocalPlayer p = client.player;
            final ClientLevel w = client.level;
            if(!handleStriderCheck(p,w,firstBuzzed)) return;
            boolean hasMyHook = !w.getEntitiesOfClass(FishingHook.class, viewBox(p),
            h -> h.getOwner() == p).isEmpty();
            boolean canCue = p.getMainHandItem().getItem() == Items.FISHING_ROD;
            long now = System.currentTimeMillis();

            boolean foundQuestion = !w.getEntitiesOfClass(Entity.class, viewBox(p),
                    e -> e != null && e.hasCustomName() && "?".equals(e.getCustomName().getString())).isEmpty();

            // start timer on cast (rising edge of bobber presence)
            if (!last && hasMyHook && canCue && !foundQuestion && timeUntilFish == 0.0f) {
                check = now + ThreadLocalRandom.current().nextLong(8000, 10001); // 2000..4000 ms
            }
            if (!last && hasMyHook && canCue && waitForSlug == 0L) {
                waitForSlug = now + ((long)timeUntilFish)* 1000L;
            }
            if(tPostCheck == -1L && !hasMyHook && canCue){
                tPostCheck = now + 1000L + ThreadLocalRandom.current().nextLong(50,201);
            }
            // cancel if conditions break before fire
            if (tPostCheck != -1L && (hasMyHook || !canCue)) {
                tPostCheck = -1L;
            }
            if(now >= tPostCheck && tPostCheck != -1L){
                tPostCheck = -1L;
                cue(2, false, now);
            }
            last = hasMyHook;

            // cancel timer if conditions break during wait
            if (foundQuestion || !hasMyHook || !canCue) {
                check = 0L;
            }

            // fire cue at/after deadline while conditions still valid
            if (check != 0L && now >= check) {
                cue(1, false, now);           // your cue()
                check = 0L;      // reset timer
            }
            if(goldenFish && canCue){
                goldenFishCheck = now + 500L + ThreadLocalRandom.current().nextLong(200,401);
                goldenFish = false;
            }
            if(goldenFishCheck != -1L && now >= goldenFishCheck){
                goldenFishCheck = -1L;
                cue(5, false, now);
            }
            // arm on presence of "!!!"
            boolean found = !w.getEntitiesOfClass(Entity.class, viewBox(p),
                    e -> e != null && e.hasCustomName() && "!!!".equals(e.getCustomName().getString())).isEmpty();
            
            if (found && !armed && now > waitForSlug) {
                armed = true;
                scheduleCues();
                toast("Armed: cues scheduled");
            }
            if (!armed) return;

            // first cue
            if (!firstBuzzed && now >= tFirstCue && tFirstCue != -1L && found && now > waitForSlug) {
                if (p.getMainHandItem().getItem() == Items.FISHING_ROD) {
                    cue(3,found, now);
                }
                firstBuzzed = true;
                waitForSlug = 0L;
                ThreadLocalRandom rng = ThreadLocalRandom.current();
                int n2 = secondArray.length;
                int rnd2 = rng.nextInt(n2);
                long d2 = (long) secondArray[rnd2] + rng.nextLong(0, 15);   // +[0..5] ms
                lastKillTime = now + 800L + rng.nextLong(400);
                tSecondCue = now + d2;
            }
            // second cue
            if (firstBuzzed && !secondBuzzed && now >= tSecondCue && tSecondCue != -1L) {
                if (p.getMainHandItem().getItem() == Items.FISHING_ROD) {
                    cue(4,found, now);
                }
                
                tPostCheck = -1L; // check in 1s
                secondBuzzed = true;
                armed = false;
                // loop: reschedule next pair immediately
            }
            // HUD countdown
            if (hudTicks > 0) hudTicks--;
        });
        registerMessageHooks();
    }

    /* ---- helpers ---- */
private static void registerMessageHooks() {
    ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, ts) -> {
        String txt = message.getString().toLowerCase(Locale.ROOT);
        if (txt.contains("the golden fish is weak!")) {
            goldenFish = true;
        }
    });

    ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
        String txt = message.getString().toLowerCase(Locale.ROOT);
        if (txt.contains("the golden fish is weak!")) {
            goldenFish = true;
        }
    });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, ts) -> {
        String txt = message.getString().toLowerCase(Locale.ROOT);
        if (txt.contains("[SkyHanni] Mouse rotation is now locked.")) {
            Farming.shouldLock = false;
        }
    });

    ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
        String txt = message.getString().toLowerCase(Locale.ROOT);
        if (txt.contains("[SkyHanni] Mouse rotation is now locked.")) {
            Farming.shouldLock = false;
        }
    });    ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, ts) -> {
        String txt = message.getString().toLowerCase(Locale.ROOT);
        if (txt.contains("[SkyHanni] Mouse rotation is now unlocked.")) {
            Farming.shouldLock = true;
        }
    });

    ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
        String txt = message.getString().toLowerCase(Locale.ROOT);
        if (txt.contains("[SkyHanni] Mouse rotation is now unlocked.")) {
            Farming.shouldLock = true;
        }
    });
}

    private static boolean handleStriderCheck(LocalPlayer p,ClientLevel w, boolean tried) {
    if (p == null || p.level() == null) return false;
    long now = System.currentTimeMillis();

    // count Stridersurfer entities
    int ssCount = w.getEntitiesOfClass(Entity.class, viewBox(p), e ->
            e != null && e.hasCustomName() &&
            e.getCustomName().getString().toLowerCase(Locale.ROOT).contains("stridersurfer")
    ).size();

    // if failed and still ≥15, halt
    if (failed && ssCount >= 15)
        return false;

    // ≥30 triggers kill flow
    if (ssCount >= 25 || midkill) {
        // first trigger: wait a bit before executing kill
        if (!tried) {
            return true;
        }

        // still waiting
        if (now < lastKillTime)
            return false;

        // execute kill once
        if (!over) {
            if (!kill(p,w))
                return false; // kill still in progress
            over = true;

            // recheck entity count
            ssCount = w.getEntitiesOfClass(Entity.class, viewBox(p), e ->
                    e != null && e.hasCustomName() &&
                    e.getCustomName().getString().toLowerCase(Locale.ROOT).contains("stridersurfer")
            ).size();

            // still too many after kill → stop, set failed
            if (ssCount >= 15) {
                killMobs = true;
                failed = true;
                return false;
            }

            // success → resume normal loop
            killMobs = false;
            failed = false;
            tried = false;
            over = false;
            firstBuzzed = true;
            secondBuzzed = true;
            armed = false;
            return true;
        }

        // already handled this kill cycle
        return false;
    }

    // if fewer than 30 striders, reset control flags
    killMobs = false;
    tried = false;
    over = false;
    if (failed){
         reset();
    return false;}
    if (failed && ssCount < 15) failed = false;
    return true; // continue normal loop
}

    private static String fmtMs(long ms) {
    if (ms < 1000) return ms + " ms";
        return String.format("%.3f s", ms / 1000.0);
    }

    private static void scheduleCues() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        long now = System.currentTimeMillis();
        int n1 = firstArray.length;
        int n2 = secondArray.length;
        int rnd1 = rng.nextInt(n1);
        int rnd2 = rng.nextInt(n2);
        long d1 = (long) firstArray[rnd1] + rng.nextLong(0, 20);   // +[0..10] ms
        long d2 = (long) secondArray[rnd2] + rng.nextLong(0, 15);   // +[0..5] ms
        lastKillTime = now + 800L + rng.nextLong(400) + d1;
        tFirstCue = now + d1;
        tSecondCue = tFirstCue + d2;
        firstBuzzed = false;
        secondBuzzed = false;

    }

    private static void loadAverages() {
    int n1 = 0, n2 = 0;

    if (Files.isRegularFile(CSV)) {
        try (var br = Files.newBufferedReader(CSV)) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length < 4) continue;
                String phase = parts[2];
                long ms;
                try { ms = Long.parseLong(parts[3]); } catch (NumberFormatException e) { continue; }

                if ("start_to_first".equals(phase)) { n1++; }
                else if ("first_to_second".equals(phase)) { n2++; }
            }
        } catch (IOException ignored) {}
    }
    firstArray = new long[n1];
    secondArray = new long[n2];
    n1 = 0; n2 = 0;
     if (Files.isRegularFile(CSV)) {
        try (var br = Files.newBufferedReader(CSV)) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length < 4) continue;
                String phase = parts[2];
                long ms;
                try { ms = Long.parseLong(parts[3]); } catch (NumberFormatException e) { continue; }

                if ("start_to_first".equals(phase)) {firstArray[n1] = ms; n1++; }
                else if ("first_to_second".equals(phase)) {secondArray[n2] = ms; n2++; }
            }
        } catch (IOException ignored) {}
    }
}


    private static void cue(int i, boolean found, long now) {
        try {
            NativeInput.rightClick();
            LOG.info("armed={}, found={}, now={}, tFirstCue={}, firstBuzzed={}, tSecondCue={}, secondBuzzed={}, place={}",
         armed, found, now, tFirstCue, firstBuzzed, tSecondCue, secondBuzzed,i);

        } catch (Exception e) {
            errorLogged = true;
        }
    }

    private static void toast(String msg) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(Component.literal(msg), true);
    }

    private static void reset() {
    armed = false;
    tFirstCue = -1L;
    tSecondCue = -1L;
    firstBuzzed = true;
    secondBuzzed = true;
    hudMsg = "";
    hudTicks = 0;
    killMobs = false;

    // averages (ms)
    avgFirst = 500.0;
    avgSecond = 300.0;

    errorLogged = false;
    goldenFish = false;
    goldenFishCheck = -1L;
    tPostCheck = -1L;
    postChecked = false;
    lastKillTime = 0L;
    over = false;
    failed = false;
    first = true;
    second = false;
    midkill = false;
    firstDelay = 0L;
    secondDelay = 0L;
    cpsDelay = 0L;
    check = 0L;
    last = false;
    }

    private static AABB viewBox(LocalPlayer p) {
        return new AABB(p.blockPosition()).inflate(64.0);
    }

    private static void drawCentered(GuiGraphics g, String text, int argb, float scale) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        var win = mc.getWindow();

        PoseStack pose = g.pose();
        pose.pushPose();
        float x = win.getGuiScaledWidth() / 2f;
        float y = win.getGuiScaledHeight() / 3f;

        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1f);

        int w = font.width(text);
        // centered, with shadow
        g.drawString(font, text, -w / 2, 0, argb, true);

        pose.popPose();
    }
    private static Boolean kill(LocalPlayer p,ClientLevel w )
    {
        midkill = true;
        long now = System.currentTimeMillis();
        if (first){
            NativeInput.pressKey(0x33);
            
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            firstDelay = rng.nextLong(500, 651) + now;
            first = false;
            return false;
        }
        if (now > firstDelay && !second){   
            if(p.getMainHandItem().getItem() != Items.STONE_AXE){
                NativeInput.pressKey(0x33);
                ThreadLocalRandom rng = ThreadLocalRandom.current();
                firstDelay = rng.nextLong(192, 372) + now;
                return false;
            }
            if(isStridersurferInHitRange(p,w)) {
                
                if(cpsDelay < now){
                    ThreadLocalRandom rng = ThreadLocalRandom.current();
                    NativeInput.leftClick();
                    cpsDelay = ((long)(1000/rng.nextLong(6, 11))) + now;
                    return false;
                }
            }
            else{
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            secondDelay = rng.nextLong(500, 651) + now;
            second = true;
            return false;
            }
        }
        if (second && now > secondDelay){
        NativeInput.pressKey(0x32);
        first = true;    
        second = false;
        midkill = false;
        return true;
        }
        return false;
    }
    private static boolean isStridersurferInHitRange(LocalPlayer p,ClientLevel w) {
    if (p == null || p.level() == null) return false;

    double reach = 3.4D; // attack reach
    Vec3 eye = p.getEyePosition();
    Vec3 look = p.getLookAngle().normalize();
    int n1 = 0, n2 = 0;
    Boolean found = false;
    for (Entity e : w.getEntitiesOfClass(Entity.class, p.getBoundingBox().inflate(reach))) {
        if (e == null || !e.hasCustomName()) continue;
        String name = e.getCustomName().getString().toLowerCase(Locale.ROOT);
        if (!name.contains("stridersurfer")) continue;
        n1++;
        Vec3 toEnt = e.position().subtract(eye);
        double dist = toEnt.length();
        if (dist > reach) continue;

        // normalize and check direction
        toEnt = toEnt.normalize();
        double dot = look.dot(toEnt);

        // ~0.707 means within ~45° cone in front of player
        if (dot > 0.85) {
            n2++;
            found = true;
        }
    }
    return found;
}

}
