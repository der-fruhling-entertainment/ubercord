package net.derfruhling.minecraft.ubercord.mixin;

import net.derfruhling.minecraft.ubercord.client.UbercordClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.LastSeenMessagesTracker;
import net.minecraft.network.chat.SignedMessageChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin extends ClientCommonPacketListenerImpl {
    @Shadow private LastSeenMessagesTracker lastSeenMessages;
    @Shadow private SignedMessageChain.Encoder signedMessageEncoder;

    protected ClientPacketListenerMixin(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie) {
        super(minecraft, connection, commonListenerCookie);
    }

    /**
     * @author der_frühling
     * @reason chat messiness
     */
    @Overwrite
    public void sendChat(String string) {
        if(string.startsWith("~")) {
            var parts = string.substring(1).split(" ", 2);
            if(parts.length < 2 || parts[1].isBlank()) return;
            UbercordClient.get().sendMessage(parts[0], parts[1]);
        } else {
            UbercordClient.get().sendMessage(string);
        }
    }
}
