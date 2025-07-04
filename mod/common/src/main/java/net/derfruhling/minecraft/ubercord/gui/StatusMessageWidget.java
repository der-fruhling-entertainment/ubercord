package net.derfruhling.minecraft.ubercord.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.widget.SpruceLabelWidget;
import net.derfruhling.minecraft.ubercord.client.StatusMessage;
import net.derfruhling.minecraft.ubercord.client.UbercordClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class StatusMessageWidget extends SpruceLabelWidget {
    private final StatusMessage ticker = UbercordClient.getStatus();
    private float partialTicksPassed = 0;

    public StatusMessageWidget(Position position, int maxWidth, Consumer<SpruceLabelWidget> action, boolean centered) {
        super(position, Component.empty(), maxWidth, action, centered);
    }

    public StatusMessageWidget(Position position, int maxWidth, Consumer<SpruceLabelWidget> action) {
        super(position, Component.empty(), maxWidth, action);
    }

    public StatusMessageWidget(Position position, int maxWidth, boolean centered) {
        super(position, Component.empty(), maxWidth, centered);
    }

    public StatusMessageWidget(Position position, int maxWidth) {
        super(position, Component.empty(), maxWidth);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        partialTicksPassed += delta;

        while (partialTicksPassed > 1) {
            partialTicksPassed -= 1.0f;
            ticker.tick();
        }

        update();
        super.render(graphics, mouseX, mouseY, delta);
    }

    protected void update() {
        setText(ticker.getFullComponent());
    }
}
