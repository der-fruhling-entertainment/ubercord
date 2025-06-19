package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LobbyIdFound(long lobbyId, String name) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LobbyIdFound> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "lobby_id_found"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LobbyIdFound> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, LobbyIdFound::lobbyId,
            ByteBufCodecs.STRING_UTF8, LobbyIdFound::name,
            LobbyIdFound::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
