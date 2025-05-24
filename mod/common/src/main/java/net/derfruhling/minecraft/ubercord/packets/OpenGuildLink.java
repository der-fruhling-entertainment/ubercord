package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenGuildLink(long lobbyId) implements CustomPacketPayload {
    public static final Type<OpenGuildLink> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "open_link_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGuildLink> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, OpenGuildLink::lobbyId,
            OpenGuildLink::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
