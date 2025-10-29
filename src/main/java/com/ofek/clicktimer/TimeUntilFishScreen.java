package com.ofek.clicktimer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public class TimeUntilFishScreen extends Screen {
    private EditBox input;
    private Button saveBtn;
    private Button toggleBtn;
    private String error = "";

    // current toggle state (mirrors ClickCueClient)
    private boolean startRight = Farming.startRight;

    public TimeUntilFishScreen() {
        super(Component.literal("Time Until Fish"));
    }

    @Override
    protected void init() {
        int w = this.width, h = this.height;

        // numeric input box
        input = new EditBox(this.font, w/2 - 100, h/2 - 30, 200, 20, Component.literal("time (seconds)"));
        input.setValue(String.valueOf(ClickCueClient.timeUntilFish));
        input.setResponder(s -> error = ""); // clear error on edit
        addRenderableWidget(input);

        // toggle button
        toggleBtn = Button.builder(Component.literal("Start Right: " + (startRight ? "ON" : "OFF")), b -> {
            startRight = !startRight;
            toggleBtn.setMessage(Component.literal("Start Right: " + (startRight ? "ON" : "OFF")));
        }).bounds(w/2 - 60, h/2, 120, 20).build();
        addRenderableWidget(toggleBtn);

        // save button
        saveBtn = Button.builder(Component.literal("Save"), b -> {
            try {
                float v = Float.parseFloat(input.getValue().trim());
                ClickCueClient.timeUntilFish = v;
                Farming.startRight = startRight;

                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("Saved: time=" + v + ", startRight=" + startRight), true);

                onClose();
            } catch (NumberFormatException e) {
                error = "Invalid number";
            }
        }).bounds(w/2 - 40, h/2 + 28, 80, 20).build();
        addRenderableWidget(saveBtn);

        setInitialFocus(input);
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
