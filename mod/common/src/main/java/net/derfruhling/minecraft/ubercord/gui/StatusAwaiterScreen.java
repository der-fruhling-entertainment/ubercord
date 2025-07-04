package net.derfruhling.minecraft.ubercord.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.screen.SpruceScreen;
import dev.lambdaurora.spruceui.widget.SpruceButtonWidget;
import net.derfruhling.minecraft.ubercord.client.DisplaysStatusMessage;
import net.derfruhling.minecraft.ubercord.client.UbercordClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class StatusAwaiterScreen extends SpruceScreen implements DisplaysStatusMessage {
    private final Runnable returnToParent;

    public StatusAwaiterScreen(Runnable returnToParent) {
        super(Component.translatable("ubercord.status_await_screen"));
        this.returnToParent = returnToParent;
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(new StatusMessageWidget(Position.of(0, height / 2), width, true));
        addRenderableWidget(new SpruceButtonWidget(Position.of((width / 2) - 80, (height / 2) + 20), 160, 20, Component.translatable("ubercord.auth.common.do_in_background"), button -> {
            onClose();
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        if(UbercordClient.getStatus().isDone()) {
            onClose();
        }
    }

    @Override
    public void onClose() {
        returnToParent.run();
    }
}
