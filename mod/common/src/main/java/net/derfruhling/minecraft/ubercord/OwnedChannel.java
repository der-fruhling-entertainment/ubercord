package net.derfruhling.minecraft.ubercord;

public record OwnedChannel(
        long lobbyId,
        String lobbyToken,
        String name,
        ManagedChannelKind kind
) {
}
