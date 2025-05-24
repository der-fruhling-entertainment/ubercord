package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExchangeProvisionalSecret(String secret) implements CustomPacketPayload {
    public static final Type<ExchangeProvisionalSecret> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "exchange_provisional_secret"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExchangeProvisionalSecret> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ExchangeProvisionalSecret::secret,
            ExchangeProvisionalSecret::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
