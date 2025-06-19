package net.derfruhling.minecraft.ubercord.client;

import net.derfruhling.discord.socialsdk4j.Lobby;
import net.derfruhling.minecraft.ubercord.ManagedChannelKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public record JoinedChannel(
        long lobbyId,
        ManagedChannelKind kind,
        String name,
        @Nullable String secret,
        Lobby lobby
) {
    private JoinedChannel(Lobby lobby, Map<String, String> meta, @Nullable String secret) {
        this(lobby.id,
                ManagedChannelKind.valueOf(meta.getOrDefault("kind", "global").toUpperCase(Locale.ROOT)),
                meta.get("name"),
                secret == null ? meta.get("secret") : secret,
                lobby);
    }

    public JoinedChannel(Lobby lobby) {
        this(lobby, lobby.getMetadata(), null);
    }

    public JoinedChannel(Lobby lobby, @NotNull String secret) {
        this(lobby, lobby.getMetadata(), secret);
    }

    public record Husk(
            ManagedChannelKind kind,
            String name
    ) {}
}
