package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record JoinLobby(long lobbyId, String permissionToken) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<JoinLobby> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "join_lobby"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JoinLobby> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, JoinLobby::lobbyId,
            ByteBufCodecs.STRING_UTF8, JoinLobby::permissionToken,
            JoinLobby::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
