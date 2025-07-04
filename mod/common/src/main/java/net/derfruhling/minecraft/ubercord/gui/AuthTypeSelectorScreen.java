package net.derfruhling.minecraft.ubercord.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.screen.SpruceScreen;
import dev.lambdaurora.spruceui.widget.SpruceButtonWidget;
import dev.lambdaurora.spruceui.widget.SpruceLabelWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public abstract class AuthTypeSelectorScreen extends SpruceScreen {
    private final Runnable returnToParent;
    private SpruceLabelWidget information;
    private SpruceButtonWidget discordButton, provisionalButton, noneButton;

    protected AuthTypeSelectorScreen(Runnable returnToParent) {
        super(Component.translatable("ubercord.auth.screen"));
        this.returnToParent = returnToParent;
    }

    protected abstract @Nullable Screen startDiscordFlow(Runnable returnToParent);
    protected abstract @Nullable Screen startProvisionalFlow(Runnable returnToParent);

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(new SpruceLabelWidget(
                Position.of(width / 10, height / 2),
                Component.translatable("ubercord.auth.screen.info"),
                (int)(width * 0.8),
                true));
        addRenderableWidget(new SpruceButtonWidget(
                Position.of(10, height - 30),
                (width / 3) - 15,
                20,
                Component.translatable("ubercord.auth.screen.discord"),
                button -> {
                    button.setActive(false);
                    Screen screen = startDiscordFlow(returnToParent);
                    if(screen != null) {
                        Minecraft.getInstance().setScreen(screen);
                    }
                }));
        addRenderableWidget(new SpruceButtonWidget(
                Position.of((width / 3) + 5, height - 30),
                (width / 3) - 15,
                20,
                Component.translatable("ubercord.auth.screen.provisional"),
                button -> {
                    button.setActive(false);
                    Screen screen = startProvisionalFlow(returnToParent);
                    if(screen != null) {
                        Minecraft.getInstance().setScreen(screen);
                    }
                }));
        addRenderableWidget(new SpruceButtonWidget(
                Position.of(((width / 3) * 2), height - 30),
                (width / 3) - 15,
                20,
                Component.translatable("ubercord.auth.screen.none"),
                button -> returnToParent.run()));
    }

    @Override
    public void onClose() {
        returnToParent.run();
    }
}
