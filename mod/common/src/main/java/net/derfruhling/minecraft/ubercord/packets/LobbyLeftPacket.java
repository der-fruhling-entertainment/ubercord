package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record LobbyLeftPacket(long lobbyId) implements CustomPacketPayload {
    public static final Type<LobbyLeftPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "lobby_left"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LobbyLeftPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, LobbyLeftPacket::lobbyId,
            LobbyLeftPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
