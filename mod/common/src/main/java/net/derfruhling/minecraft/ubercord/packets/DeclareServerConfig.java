package net.derfruhling.minecraft.ubercord.packets;

import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
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

import java.util.*;

public record DeclareServerConfig(@Nullable DisplayConfig config, String[] availableChannels) implements CustomPacketPayload {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public DeclareServerConfig(Optional<CompoundTag> config, String[] availableChannels) {
        this(config.map(DisplayConfig::decode).orElse(null), availableChannels);
    }

    public DeclareServerConfig(ServerConfig config) {
        this(config.display(), config.channels());
    }

    public static final Type<DeclareServerConfig> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Ubercord.MOD_ID, "configure"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DeclareServerConfig> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.OPTIONAL_COMPOUND_TAG, (DeclareServerConfig c) -> c.config() != null ? Optional.of(c.config().toCompoundTag()) : Optional.<CompoundTag>empty(),
            ByteBufCodecs.<ByteBuf, String, List<String>>collection(ArrayList::new)
                    .apply(ByteBufCodecs.STRING_UTF8)
                    .map(strings -> strings.toArray(new String[0]), Lists::newArrayList), DeclareServerConfig::availableChannels,
            DeclareServerConfig::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
