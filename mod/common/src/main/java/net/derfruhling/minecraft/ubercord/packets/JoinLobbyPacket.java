package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record JoinLobbyPacket(long lobbyId, String lobbyName) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<JoinLobbyPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "join_lobby"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JoinLobbyPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, JoinLobbyPacket::lobbyId,
            ByteBufCodecs.STRING_UTF8, JoinLobbyPacket::lobbyName,
            JoinLobbyPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
