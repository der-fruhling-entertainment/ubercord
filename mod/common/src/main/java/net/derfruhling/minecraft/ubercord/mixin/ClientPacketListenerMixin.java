package net.derfruhling.minecraft.ubercord.mixin;

import net.derfruhling.minecraft.ubercord.client.CanSendSignedMessage;
import net.derfruhling.minecraft.ubercord.client.UbercordClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.LastSeenMessagesTracker;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.util.Crypt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.time.Instant;

@Mixin(ClientPacketListener.class)
@Environment(EnvType.CLIENT)
public abstract class ClientPacketListenerMixin extends ClientCommonPacketListenerImpl implements CanSendSignedMessage {
    @Shadow private LastSeenMessagesTracker lastSeenMessages;
    @Shadow private SignedMessageChain.Encoder signedMessageEncoder;

    protected ClientPacketListenerMixin(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie) {
        super(minecraft, connection, commonListenerCookie);
    }

    @Override
    public void sendSignedMessageToServer(String message) {
        Instant instant = Instant.now();
        long salt = Crypt.SaltSupplier.getLong();
        LastSeenMessagesTracker.Update update = this.lastSeenMessages.generateAndApplyUpdate();
        MessageSignature messageSignature = this.signedMessageEncoder.pack(new SignedMessageBody(message, instant, salt, update.lastSeen()));
        this.send(new ServerboundChatPacket(message, instant, salt, messageSignature, update.update()));
    }

    /**
     * @author der_frühling
     * @reason chat messiness
     */
    @Overwrite
    public void sendChat(String message) {
        if(message.startsWith("~")) {
            var parts = message.substring(1).split(" ", 2);
            if(parts.length < 2 || parts[1].isBlank()) return;
            UbercordClient.get().sendMessage(parts[0], parts[1]);
        } else {
            UbercordClient.get().sendMessage(message);
        }
    }
}
