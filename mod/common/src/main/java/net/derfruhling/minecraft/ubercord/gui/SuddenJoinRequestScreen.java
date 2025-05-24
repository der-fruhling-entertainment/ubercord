package net.derfruhling.minecraft.ubercord.gui;

import net.derfruhling.discord.socialsdk4j.ActivityInvite;
import net.derfruhling.discord.socialsdk4j.Client;
import net.derfruhling.minecraft.ubercord.client.SocialSdkIntegration;
import net.derfruhling.minecraft.ubercord.client.UbercordClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class SuddenJoinRequestScreen extends SuddenInviteScreen {
    private static final Logger log = LogManager.getLogger(SuddenJoinRequestScreen.class);

    public SuddenJoinRequestScreen(Screen parent, ActivityInvite invite) {
        super(parent, invite);
    }

    @Override
    protected @NotNull MutableComponent getTitleLabel() {
        return Component.translatable("ubercord.invite.screen.join_request");
    }

    @Override
    protected @NotNull MutableComponent getSenderLabel(String displayName) {
        return Component.translatable("ubercord.invite.screen.joiner", displayName);
    }

    @Override
    protected void acceptInvite() {
        SocialSdkIntegration sdk = UbercordClient.get();
        Client client = sdk.getClient();
        ActivityInvite invite = new ActivityInvite(sdk.self.id, 0, 0, ActivityInvite.ActionType.Join, sdk.getCurrentClientId(), this.invite.partyId(), this.invite.sessionId());
        client.sendActivityJoinRequestReply(invite, (result) -> {
            if (result.isSuccess()) {
                onClose();
            } else {
                log.error("Failed to accept activity join request {}: {}", invite.messageId(), result.message());
            }
        });
    }
}
