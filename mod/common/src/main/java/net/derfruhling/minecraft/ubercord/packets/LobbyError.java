package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LobbyError(long lobbyId, String lobbyName) implements CustomPacketPayload {
    public static final Type<LobbyError> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "lobby_error"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LobbyError> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, LobbyError::lobbyId,
            ByteBufCodecs.STRING_UTF8, LobbyError::lobbyName,
            LobbyError::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
