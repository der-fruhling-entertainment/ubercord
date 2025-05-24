package net.derfruhling.minecraft.ubercord.packets;

import com.google.gson.Gson;
import net.derfruhling.minecraft.ubercord.DisplayConfig;
import net.derfruhling.minecraft.ubercord.DisplayMode;
import net.derfruhling.minecraft.ubercord.Ubercord;
import net.derfruhling.minecraft.ubercord.server.ServerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record DeclareServerConfig(long clientId, @Nullable DisplayConfig config) implements CustomPacketPayload {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public DeclareServerConfig(long clientId, Optional<CompoundTag> config) {
        this(clientId, config.map(DisplayConfig::decode).orElse(null));
    }

    public DeclareServerConfig(ServerConfig config) {
        this(config.clientId(), config.display());
    }

    public static final Type<DeclareServerConfig> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "configure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeclareServerConfig> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DeclareServerConfig::clientId,
            ByteBufCodecs.OPTIONAL_COMPOUND_TAG, (DeclareServerConfig c) -> c.config() != null ? Optional.of(c.config().toCompoundTag()) : Optional.empty(),
            DeclareServerConfig::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
