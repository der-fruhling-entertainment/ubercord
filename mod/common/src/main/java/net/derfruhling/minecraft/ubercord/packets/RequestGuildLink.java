package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestGuildLink(long lobbyId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestGuildLink> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "request_guild_link"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestGuildLink> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, RequestGuildLink::lobbyId,
            RequestGuildLink::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
