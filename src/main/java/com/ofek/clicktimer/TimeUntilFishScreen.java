package com.ofek.clicktimer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public class TimeUntilFishScreen extends Screen {
    private EditBox inputTime;      // was: input
    private EditBox inputMaxWait;   // new
    private Button saveBtn;
    private Button toggleBtn;
    private String error = "";
    private Button saveAnglesBtn;
    private Button saveItemBtn;

    // current toggle state (mirrors ClickCueClient)
    private boolean startRight = Farming.startRight;
    private float maxWait = Farming.MaxWaitMs;

    public TimeUntilFishScreen() {
        super(Component.literal("Time Until Fish"));
    }

    @Override
    protected void init() {
        int w = this.width, h = this.height;

        // numeric input box
        inputTime = new EditBox(this.font, w/2 - 100, h/2 - 50, 200, 20, Component.literal("time (seconds)"));
        inputTime.setValue(String.valueOf(ClickCueClient.timeUntilFish));
        inputTime.setResponder(s -> error = "");
        addRenderableWidget(inputTime);
        // Farming.MaxWaitMs (milliseconds)
        inputMaxWait = new EditBox(this.font, w/2 - 100, h/2 - 24, 200, 20, Component.literal("MaxWait (ms)"));
        inputMaxWait.setValue(String.valueOf(Farming.MaxWaitMs));
        inputMaxWait.setResponder(s -> error = "");
        addRenderableWidget(inputMaxWait);
        // toggle button
        toggleBtn = Button.builder(Component.literal("Start Right: " + (startRight ? "ON" : "OFF")), b -> {
            startRight = !startRight;
            toggleBtn.setMessage(Component.literal("Start Right: " + (startRight ? "ON" : "OFF")));
        }).bounds(w/2 - 60, h/2, 120, 20).build();
        addRenderableWidget(toggleBtn);
        saveAnglesBtn = Button.builder(Component.literal("Save Yaw/Pitch"), b -> {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            Farming.SavedYaw = mc.player.getYRot();   // yaw
            Farming.SavedPitch = mc.player.getXRot(); // pitch
            Farming.SavedAngles = true;
            mc.player.displayClientMessage(
            Component.literal("Saved yaw=" + Farming.SavedYaw + ", pitch=" + Farming.SavedPitch), true);
        }
        }).bounds(w/2 - 100, h/2 + 52, 200, 20).build();
        addRenderableWidget(saveAnglesBtn);
            saveItemBtn = Button.builder(Component.literal("Save Held Item Name"), b -> {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            var st = mc.player.getMainHandItem();
            if (!st.isEmpty()) {
                Farming.LastItemName = st.getHoverName().getString();
                mc.player.displayClientMessage(
                    Component.literal("Saved item: " + Farming.LastItemName), true);
            } else {
                mc.player.displayClientMessage(Component.literal("No item in main hand"), true);
            }
        }
        }).bounds(w/2 - 100, h/2 + 76, 200, 20).build();
        addRenderableWidget(saveItemBtn);
        // save button
        saveBtn = Button.builder(Component.literal("Save"), b -> {
            try {
                float v = Float.parseFloat(inputTime.getValue().trim());
                ClickCueClient.timeUntilFish = v;
                Farming.startRight = startRight;
                Farming.MaxWaitMs = maxWait;

                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("Saved: time=" + v + ", startRight=" + startRight), true);

                onClose();
            } catch (NumberFormatException e) {
                error = "Invalid number";
            }
        }).bounds(w/2 - 40, h/2 + 28, 80, 20).build();
        addRenderableWidget(saveBtn);

        setInitialFocus(inputTime);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        super.render(g, mouseX, mouseY, delta);

        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 60, 0xFFFFFF);
        if (!error.isEmpty()) {
            g.drawCenteredString(this.font, error, this.width / 2, this.height / 2 + 54, 0xFF5555);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
