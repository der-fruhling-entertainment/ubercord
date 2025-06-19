package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LeaveLobby(long lobbyId) implements CustomPacketPayload {
    public static final Type<LeaveLobby> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "leave_lobby"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LeaveLobby> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, LeaveLobby::lobbyId,
            LeaveLobby::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
