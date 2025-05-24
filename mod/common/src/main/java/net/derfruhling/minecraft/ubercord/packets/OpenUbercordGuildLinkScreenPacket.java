package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class OpenUbercordGuildLinkScreenPacket implements CustomPacketPayload {
    public static final OpenUbercordGuildLinkScreenPacket INSTANCE = new OpenUbercordGuildLinkScreenPacket();

    public static final Type<OpenUbercordGuildLinkScreenPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "open_link_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenUbercordGuildLinkScreenPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
