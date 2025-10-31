package com.ofek.clicktimer;

import com.ofek.clicktimer.TimeQueue.First;
import com.ofek.nativeinput.NativeInput;
import com.sun.jna.platform.win32.WinUser;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;

import org.lwjgl.glfw.GLFW;
import net.minecraft.world.entity.player.Player;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.annotation.Native;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.Vec3;
public class Farming{
    private static final Path LOG_PATH = FabricLoader.getInstance().getGameDir().resolve("clicktimer").resolve("space_intervals.csv");
    private static long lastSpaceNanos = 0L;
    private static boolean hasLastTime = false;
    private static double[] intervals = new double[0];
    private static int evenCount = 0;
    private static int oddCount  = 0;
    private static boolean intervalsLoaded = false;
    private static boolean Reseted = true;
    private static KeyMapping farmingAutoKey;
    private static KeyMapping farmingEazyKey;
    private static KeyMapping changeDirectionKey;
    public static boolean startRight = true;  // no reset
    public static boolean activeAuto = false;
    public static boolean activeMenual = false;
    public static First Timer = null;
    public static TimeQueue queue = new TimeQueue();
    public static boolean first = true;
    public static ArrayList<Integer> down = new ArrayList<>();
    public static boolean goingRight = startRight;
    public static boolean stopped = false;
    public static Vec3 cords = null;
    public static float MaxWaitMs = 2000f; // no reset
    public static float SavedYaw = 0f;   // player.getYRot() no reset
    public static float SavedPitch = 0f; // player.getXRot() no reset
    public static boolean SavedAngles = false; // no reset
    public static boolean checked = false;
    public static String LastItemName = ""; // no reset
    public static boolean loadItemName = false; //no reset
    public static boolean shouldLock = false; // no reset
    public static double yValue = -1000L; // no reset
    private static final int VK_A = 0x41;
    private static final int VK_D = 0x44;
    private static final int VK_W = 0x57;
    public static void init() {
    farmingAutoKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "farmingA", GLFW.GLFW_KEY_P, "key.categories.misc"));
    farmingEazyKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "farmingE", GLFW.GLFW_KEY_P, "key.categories.misc"));
    changeDirectionKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
        "changeDirection", GLFW.GLFW_KEY_P, "key.categories.misc"));
    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      if (client.player == null || client.level == null)
      {
        if(!Reseted){
          Reseted = true;
          reset();
        }
      return;
    }
      tick(client);
    });
    intervalsLoaded = loadIntervals();
    initLog();
  }
  public static void tick(Minecraft client) {
    Reseted = false;
    // key handling
    if (farmingAutoKey.consumeClick()) {
        var p = client.player;
        if (activeAuto) {
          reset();
        } else if (!activeMenual) {
            activeAuto = true;
        }
        // else: activeManual is on → do nothing
    }
    if(farmingEazyKey.consumeClick())
      if(activeMenual){
        activeMenual = false;
        reset();
      } else
      if(!activeAuto){
        activeMenual = !activeMenual;
        activeAuto = false;
      }
    if(loadItemName && !LastItemName.equals(client.player.getMainHandItem().getHoverName().getString()) && !checked)
      return;
    activeAuto = activeAuto && intervalsLoaded;
    if(activeAuto && !stopped){
      auto(client);
    }
    else if(activeMenual)
        menual(client);
  }
  public static void auto(Minecraft clinet){
    if(shouldLock)
      sendClientCommand("shlockmouse");
    if(Math.abs(clinet.player.position().y - yValue) > 2.0 && !checked && yValue > -900L){
      sendClientCommand("warp garden");
      reset();
      checked = true;
      queue.addTime(((long)getRandomInterval(false) )+ 1000L, 2);
      activeAuto = true;
      first = false;
      return;
    }
    float Yaw = clinet.player.getYRot();
    float Pitch = clinet.player.getXRot();
    if((Math.abs(Yaw - SavedYaw) > 0.5 || Math.abs(Pitch - SavedPitch) > 0.5) && !checked && SavedAngles){
        queue.addTime(((long)getRandomInterval(false) )+ 1000L, 2);
        checked = true;
    }
    
    long now = System.currentTimeMillis();
    if(first){
        first = false;
        checked = false;
        int[] keys = {startRight? VK_D : VK_A, VK_W, NativeInput.LEFTDOWN};
        int[] types = {1,1,0};
        down.add(keys[0]);
        down.add(keys[1]);
        down.add(NativeInput.LEFTUP);
        NativeInput.custom(keys, types);
        goingRight = !startRight;
        queue.addTime((long) getRandomInterval(true) % (long)MaxWaitMs, 0); // initial delay
    }
    if(Timer == null){
      Timer = queue.isEmpty() ? null : queue.getFirst();
    }
    if(Timer != null && Timer.remainingMs < now){
        switch (Timer.codeCase) {
          case 0://key up
            if(cords == null || clinet.player.position().distanceToSqr(cords) > 1e-5){
              cords = clinet.player.position();
              queue.addTime((long)MaxWaitMs, 0);
              break;
            }
            NativeInput.custom(new int[]{(int) down.get(0)}, new int[]{2});
            down.set(0,0);
            queue.addTime((long)getRandomInterval(false), 1);
            break;
          case 1://key down
            NativeInput.custom(new int[]{goingRight? VK_D : VK_A}, new int[]{1});
            down.set(0,goingRight? VK_D : VK_A);
            goingRight = !goingRight;
            queue.addTime((long)getRandomInterval(true) % (long)MaxWaitMs, 0);
            break;
          case 2:
            NativeInput.pressKey(0x33);
            queue.addTime((long)getRandomInterval(false) % 200L +50L, 3);
            break;
          case 3:
            NativeInput.leftClick();
            queue.addTime((long)getRandomInterval(false) % 1000L + 200L, 4);
            break;
          case 4:
            NativeInput.pressKey(0x32);
            reset();
            activeAuto = true;
          default:
            break;
        }
        Timer = null;
    }
  }
  public static void menual(Minecraft client){
    if(first){
        first = false;
        int[] keys = {startRight? VK_D : VK_A, VK_W, NativeInput.LEFTDOWN};
        int[] types = {1,1,0};
        down.add(keys[0]);
        down.add(keys[1]);
        down.add(NativeInput.LEFTUP);
        NativeInput.custom(keys, types);
        goingRight = startRight;
    }
    if(changeDirectionKey.consumeClick()){
      long now = System.nanoTime();
      if (hasLastTime) {
        double delta = (now - lastSpaceNanos) / 1_000_000_000.0;
        if (!(delta < 10 || (65 < delta && delta < 120))) delta = -1;
          appendInterval(delta);
        }
      lastSpaceNanos = now;
      hasLastTime = true;

      if(((int) down.get(0)) == 0){
        goingRight = !goingRight;
        NativeInput.custom(new int[]{goingRight? VK_A : VK_D}, new int[]{1});
        down.set(0,goingRight? VK_A : VK_D);
      }  else {
        NativeInput.custom(new int[]{(int)down.get(0)}, new int[]{NativeInput.KEYEVENTF_KEYUP});
        down.set(0,0);
      }
    }
  }
  public static void reset(){
    checked = false;
    lastSpaceNanos = 0L;
    hasLastTime = false;
    intervals = new double[0];
    evenCount = 0;
    oddCount  = 0;
    stopped = false;
    cords = null;
    intervalsLoaded = false;
    activeAuto = false;
    activeMenual = false;
    Timer = null;
    queue = new TimeQueue();
    first = true;
    int size = down.size();
    for(int i = 0; i < size; i++){
        if(((int) down.get(i)) == 0){
            down.remove(i);
            i--;
            size--;
        }
    }
    if(down.size() != 0)
    {
    int[] keys = new int[down.size()];
    int[] types = new int[down.size()];
    for(int i = 0; i < down.size(); i++){
        keys[i] = (int) down.get(i);
        if(keys[i] == NativeInput.LEFTUP || keys[i] == NativeInput.RIGHTUP){
            types[i] = 0;
        } else
          types[i] = NativeInput.KEYEVENTF_KEYUP;
    }
  
    NativeInput.custom(keys, types);
  }
    down.clear();
    goingRight = startRight;
    intervalsLoaded = loadIntervals();
    initLog();
  }
  private static boolean loadIntervals() {
        File f = LOG_PATH.toFile();
        if (Files.notExists(LOG_PATH)) {
            System.out.println("No timing intervals file found.");
            intervals = new double[0];
            evenCount = 0; oddCount = 0;
            return false;
        }
        List<Double> vals = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line = br.readLine(); // header or first data
            if (line != null && !(line.contains("timestamp") || line.contains("delta"))) {
                parseAndAdd(vals, line);
            }
            String s;
            while ((s = br.readLine()) != null) parseAndAdd(vals, s);
        } catch (IOException ignored) { return false;}
        intervals = vals.stream().mapToDouble(Double::doubleValue).toArray();
        evenCount = (intervals.length + 1) / 2;
        oddCount  = intervals.length / 2;
        System.out.printf("Loaded %d timing intervals from %s%n", intervals.length, LOG_PATH);
        System.out.printf("Even indices: %d, Odd indices: %d%n", evenCount, oddCount);
        return true;
    }
      private static double getRandomInterval(boolean useEven) {
        if (intervals.length == 0) return 0;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (useEven && evenCount > 0) {
            int choice = r.nextInt(evenCount);
            return intervals[choice * 2] * 1000 + r.nextLong(1000);
        }
        if (!useEven && oddCount > 0) {
            int choice = r.nextInt(oddCount);
            return intervals[choice * 2 + 1] * 1000 + r.nextLong(50);
        }
        return intervals[r.nextInt(intervals.length)];
    }
    private static void initLog() {
    File f = LOG_PATH.toFile();
    if (!f.exists()) {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            pw.println("timestamp_utc,delta_seconds");
        } catch (IOException ignored) {return;}
    }
    return;
  }
private static synchronized void appendInterval(double delta) {
    try (FileWriter fw = new FileWriter(LOG_PATH.toFile(), true)) {
        fw.write(String.format(Locale.US, "%.3f%n", delta));
    } catch (IOException ignored) {}
}
    private static void parseAndAdd(List<Double> vals, String line) {
        try {
            double v = Double.parseDouble(line.trim());
            if (v > 0) vals.add(v);
        } catch (NumberFormatException ignored) {}
    }
     public static void sendClientCommand(String command) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            mc.getConnection().sendCommand(command);
        }
    }
}