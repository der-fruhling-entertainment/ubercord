package net.derfruhling.minecraft.ubercord.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.screen.SpruceScreen;
import dev.lambdaurora.spruceui.widget.SpruceButtonWidget;
import dev.lambdaurora.spruceui.widget.SpruceLabelWidget;
import net.derfruhling.discord.socialsdk4j.ActivityInvite;
import net.derfruhling.discord.socialsdk4j.User;
import net.derfruhling.minecraft.ubercord.client.SocialSdkIntegration;
import net.derfruhling.minecraft.ubercord.client.UbercordClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class SuddenInviteScreen extends SpruceScreen {
    private static final Logger log = LogManager.getLogger(SuddenInviteScreen.class);
    protected final Screen parent;
    protected final ActivityInvite invite;
    protected final User user;

    protected @NotNull MutableComponent getTitleLabel() {
        return Component.translatable("ubercord.invite.screen.title");
    }

    protected @NotNull MutableComponent getSenderLabel(String displayName) {
        return Component.translatable("ubercord.invite.screen.sender", displayName);
    }

    public SuddenInviteScreen(Screen parent, ActivityInvite invite) {
        super(Component.translatable("ubercord.invite.screen.title"));
        this.parent = parent;
        this.invite = invite;
        this.user = UbercordClient.get().getClient().getUser(invite.senderId());
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(new SpruceLabelWidget(Position.of(width / 2, (height / 2) - 16), getTitleLabel(), width - 10, true));
        String displayName = this.user.getDisplayName();
        addRenderableWidget(new SpruceLabelWidget(Position.of(width / 2, (height / 2) - 8), getSenderLabel(displayName), width - 10, true));
        addRenderableWidget(new SpruceButtonWidget(Position.of((width / 2) - 55, (height / 2) + 8), 50, 20, Component.translatable("ubercord.invite.screen.ignore"), button -> {
            onClose();
        }));
        addRenderableWidget(new SpruceButtonWidget(Position.of((width / 2) + 5, (height / 2) + 8), 50, 20, Component.translatable("ubercord.invite.screen.accept"), button -> {
            acceptInvite();
        }));
    }

    protected void acceptInvite() {
        UbercordClient.get().getClient().acceptActivityInvite(invite, (result, joinSecret) -> {
            if(result.isSuccess()) {
                SocialSdkIntegration.connectWithJoinSecret(joinSecret);
            } else {
                log.error("Failed to accept activity invite {}: {}", invite.messageId(), result.message());
            }
        });
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
