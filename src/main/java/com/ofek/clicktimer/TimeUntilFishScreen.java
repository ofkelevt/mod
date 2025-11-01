package com.ofek.clicktimer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TimeUntilFishScreen extends Screen {
    private EditBox inputTime;
    private EditBox inputMaxWait;
    private EditBox WarningFarming;
    private EditBox DisableFarming;
    private Button saveBtn, toggleBtn, saveAnglesBtn, saveItemBtn, getYBtn;

    private String error = "";
    private boolean startRight = Farming.startRight;
    private double yValue = Farming.yValue;

    public TimeUntilFishScreen() { super(Component.literal("Time Until Fish")); }

    @Override
    protected void init() {
        int w = this.width, h = this.height;

        // timeUntilFish (seconds)
        inputTime = new EditBox(this.font, w/2 - 100, h/2 - 50, 200, 20, Component.literal("time (seconds)"));
        inputTime.setValue(String.valueOf(ClickCueClient.timeUntilFish));
        inputTime.setResponder(s -> error = "");
        addRenderableWidget(inputTime);

        // MaxWaitMs (milliseconds)
        inputMaxWait = new EditBox(this.font, w/2 - 100, h/2 - 24, 200, 20, Component.literal("MaxWait (ms)"));
        inputMaxWait.setValue(String.valueOf(Farming.MaxWaitMs));
        inputMaxWait.setResponder(s -> error = "");
        addRenderableWidget(inputMaxWait);

        // Start Right toggle
        toggleBtn = Button.builder(Component.literal("Start Right: " + (startRight ? "ON" : "OFF")), b -> {
            startRight = !startRight;
            toggleBtn.setMessage(Component.literal("Start Right: " + (startRight ? "ON" : "OFF")));
        }).bounds(w/2 - 60, h/2, 120, 20).build();
        addRenderableWidget(toggleBtn);
        getYBtn = Button.builder(Component.literal("Get Y value"), b -> {
            var mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    Component.literal("set yValue to " + mc.player.position().y), true);
                yValue = mc.player.position().y;
            }
        }).bounds(w/2 - 100, h/2 + 28, 200, 20).build();
        addRenderableWidget(getYBtn);
        // Save yaw/pitch
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

        // Save held item name
        saveItemBtn = Button.builder(Component.literal("Save Held Item Name"), b -> {
            var mc = Minecraft.getInstance();
            if (mc.player != null) {
                var st = mc.player.getMainHandItem();
                if (!st.isEmpty()) {
                    Farming.LastItemName = st.getHoverName().getString();
                    Farming.loadItemName = true;
                    mc.player.displayClientMessage(Component.literal("Saved item: " + Farming.LastItemName), true);
                } else {
                    mc.player.displayClientMessage(Component.literal("No item in main hand"), true);
                }
            }
        }).bounds(w/2 - 100, h/2 + 76, 200, 20).build();
        addRenderableWidget(saveItemBtn);
        WarningFarming = new EditBox(this.font, w/2 - 100, h/2 + 110, 200, 20, Component.literal("warning level"));
        WarningFarming.setValue(String.valueOf(Farming.warningLvl));
        WarningFarming.setResponder(s -> error = "");
        addRenderableWidget(WarningFarming);
        DisableFarming = new EditBox(this.font, w/2 - 100, h/2 + 135, 200, 20, Component.literal("disable level"));
        DisableFarming.setValue(String.valueOf(Farming.disableLvl));
        DisableFarming.setResponder(s -> error = "");
        addRenderableWidget(DisableFarming);
        // Save button at the bottom
        saveBtn = Button.builder(Component.literal("Save"), b -> {
            try {
                float timeSec = Float.parseFloat(inputTime.getValue().trim());
                float maxWaitMs = Float.parseFloat(inputMaxWait.getValue().trim());

                ClickCueClient.timeUntilFish = timeSec;
                Farming.startRight = startRight;
                Farming.MaxWaitMs = maxWaitMs;
                Farming.yValue = yValue;
                var pl = Minecraft.getInstance().player;
                if (pl != null) {
                    pl.displayClientMessage(
                        Component.literal("Saved: time=" + timeSec + "s, MaxWaitMs=" + maxWaitMs + ", startRight=" + startRight), true);
                }
                onClose();
            } catch (NumberFormatException e) {
                error = "Invalid number";
            }
        }).bounds(w/2 - 40, h/2 + 160, 80, 20).build(); // bottom
        addRenderableWidget(saveBtn);

        setInitialFocus(inputTime);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        super.render(g, mouseX, mouseY, delta);

        g.drawCenteredString(this.font, this.title, this.width/2, this.height/2 - 76, 0xFFFFFF);
        if (!error.isEmpty()) {
            g.drawCenteredString(this.font, error, this.width/2, this.height/2 + 54, 0xFF5555);
        }

        // Labels near buttons
        // Yaw/Pitch label next to Save Yaw/Pitch button
        String ang = String.format("Yaw: %.1f  Pitch: %.1f", Farming.SavedYaw, Farming.SavedPitch);
        g.drawCenteredString(this.font, ang, this.width/2, this.height/2 + 52 + 22, 0xFFAAAAFF); // just below the button

        // Saved item name label next to Save Held Item Name
        String item = Farming.LastItemName.isEmpty() ? "(item: none)" : ("Item: " + Farming.LastItemName);
        g.drawCenteredString(this.font, item, this.width/2, this.height/2 + 76 + 22, 0xFFAAFFAA);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
