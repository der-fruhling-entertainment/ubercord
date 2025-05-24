package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BeginProvisionalAuthorizationFlow(String state) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BeginProvisionalAuthorizationFlow> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "begin_provisional_flow"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BeginProvisionalAuthorizationFlow> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BeginProvisionalAuthorizationFlow::state,
            BeginProvisionalAuthorizationFlow::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
