package net.derfruhling.minecraft.ubercord.client;

import net.derfruhling.discord.socialsdk4j.Lobby;

public record JoinedChannel(
        long lobbyId,
        Context context,
        String name,
        String secret,
        Lobby lobby
) {
    public JoinedChannel(Husk husk, String secret, long lobbyId, Lobby lobby) {
        this(lobbyId, husk.context, husk.name, secret, lobby);
    }

    public enum Context {
        Global,
        Server
    }

    public record Husk(
            Context context,
            String name
    ) {}
}
