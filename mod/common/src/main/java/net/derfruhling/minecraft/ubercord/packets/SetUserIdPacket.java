package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetUserIdPacket(long userId, boolean isProvisional) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetUserIdPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "declare_user_id"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetUserIdPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SetUserIdPacket::userId,
            ByteBufCodecs.BOOL, SetUserIdPacket::isProvisional,
            SetUserIdPacket::new
    );

    public static final SetUserIdPacket DISCARD = new SetUserIdPacket(0, false);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
