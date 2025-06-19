package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LobbyJoinFailure(long lobbyId, String lobbyName) implements CustomPacketPayload {
    public static final Type<LobbyJoinFailure> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "lobby_join_failure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LobbyJoinFailure> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, LobbyJoinFailure::lobbyId,
            ByteBufCodecs.STRING_UTF8, LobbyJoinFailure::lobbyName,
            LobbyJoinFailure::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
