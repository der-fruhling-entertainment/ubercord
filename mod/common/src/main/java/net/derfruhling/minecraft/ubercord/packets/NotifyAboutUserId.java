package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.Ubercord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record NotifyAboutUserId(String username, UUID uuid, long userId, boolean isProvisional) implements CustomPacketPayload {
    public static final Type<NotifyAboutUserId> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "server_notify_user_id"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NotifyAboutUserId> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NotifyAboutUserId::username,
            ByteBufCodecs.VAR_LONG, (NotifyAboutUserId v) -> v.uuid.getMostSignificantBits(),
            ByteBufCodecs.VAR_LONG, (NotifyAboutUserId v) -> v.uuid.getLeastSignificantBits(),
            ByteBufCodecs.VAR_LONG, NotifyAboutUserId::userId,
            ByteBufCodecs.BOOL, NotifyAboutUserId::isProvisional,
            (username, uuidMost, uuidLeast, userId, isProvisional) ->
                    new NotifyAboutUserId(username, new UUID(uuidMost, uuidLeast), userId, isProvisional)
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
