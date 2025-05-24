package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record LobbyJoinedPacket(long lobbyId, String lobbyName) implements CustomPacketPayload {
    public static final Type<LobbyJoinedPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "lobby_joined"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LobbyJoinedPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, LobbyJoinedPacket::lobbyId,
            ByteBufCodecs.STRING_UTF8, LobbyJoinedPacket::lobbyName,
            LobbyJoinedPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
