package net.derfruhling.minecraft.ubercord.packets;

import net.derfruhling.minecraft.ubercord.DisplayConfig;
import net.derfruhling.minecraft.ubercord.Ubercord;
import net.derfruhling.minecraft.ubercord.server.ServerConfig;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public record DeclareServerConfig(UUID serverConfigId, @Nullable DisplayConfig config) implements CustomPacketPayload {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public DeclareServerConfig(UUID serverConfigId, Optional<CompoundTag> config) {
        this(serverConfigId, config.map(DisplayConfig::decode).orElse(null));
    }

    public DeclareServerConfig(ServerConfig config) {
        this(config.serverConfigId(), config.display());
    }

    public static final Type<DeclareServerConfig> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "configure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeclareServerConfig> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, DeclareServerConfig::serverConfigId,
            ByteBufCodecs.OPTIONAL_COMPOUND_TAG, (DeclareServerConfig c) -> c.config() != null ? Optional.of(c.config().toCompoundTag()) : Optional.empty(),
            DeclareServerConfig::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
