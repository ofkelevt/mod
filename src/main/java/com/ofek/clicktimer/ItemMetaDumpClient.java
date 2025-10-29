package com.ofek.clicktimer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.*;

public final class ItemMetaDumpClient implements ClientModInitializer {
    private static KeyMapping dumpKey;
    private static final String KEY_CATEGORY = "key.categories.clicktimer"; // custom category

    @Override
    public void onInitializeClient() {
        dumpKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.clicktimer.dump_item_meta", GLFW.GLFW_KEY_P, KEY_CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (dumpKey.consumeClick()) dumpHeldItemMeta(client);
        });
    }

    private static void dumpHeldItemMeta(Minecraft mc) {
        ItemStack st = mc.player.getMainHandItem();
        if (st.isEmpty()) {
            toast("No item in main hand");
            return;
        }

        // Basic identity
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(st.getItem());
        String name = st.getHoverName().getString();
        int count = st.getCount();

        // Full stack NBT as SNBT (pretty text)
        CompoundTag tag = st.save(Minecraft.getInstance().level.registryAccess());
        String snbt = new SnbtPrinterTagVisitor().visit(tag);

        // Write to file
        Path outDir = mc.gameDirectory.toPath().resolve("clicktimer");
        Path out    = outDir.resolve("item_meta.txt");
        try {
            Files.createDirectories(outDir);
            String block =
                    "==== Item Meta Dump ====\n" +
                    "Name: " + name + "\n" +
                    "ID:   " + id + "\n" +
                    "Count:" + count + "\n" +
                    "SNBT:\n" + snbt + "\n\n";
            Files.writeString(out, block, CREATE, WRITE, APPEND);
            toast("Wrote meta to clicktimer/item_meta.txt");
        } catch (IOException e) {
            toast("Write failed: " + e.getMessage());
        }
    }

    private static void toast(String msg) {
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.displayClientMessage(Component.literal(msg), true);
    }
}
