package net.derfruhling.minecraft.ubercord;

import java.util.concurrent.CompletableFuture;

public non-sealed class CustomManagedChannelService extends ManagedChannelService {
    private final long clientId;
    private final String botToken;

    public CustomManagedChannelService(long clientId, String botToken) {
        this.clientId = clientId;
        this.botToken = botToken;
    }

    // TODO

    @Override
    public CompletableFuture<OwnedChannel> createServerChannel(String name) throws ChannelExistsException {
        return super.createServerChannel(name);
    }

    @Override
    public CompletableFuture<OwnedChannel> createPersonalChannel(String name, String bearerToken) throws ChannelExistsException {
        return super.createPersonalChannel(name, bearerToken);
    }

    @Override
    public CompletableFuture<Void> deleteChannel(OwnedChannel channel) {
        return super.deleteChannel(channel);
    }

    @Override
    public CompletableFuture<String> requestPermissionsToken(long lobbyId, ManagedChannelKind expectedKind, String bearerToken) {
        return super.requestPermissionsToken(lobbyId, expectedKind, bearerToken);
    }

    @Override
    public CompletableFuture<Void> addUserToChannel(OwnedChannel channel, long userId, String permissionsToken, boolean canLink) {
        return super.addUserToChannel(channel, userId, permissionsToken, canLink);
    }

    @Override
    public CompletableFuture<Void> removeUserFromChannel(OwnedChannel channel, long userId) {
        return super.removeUserFromChannel(channel, userId);
    }

    @Override
    public CompletableFuture<Void> removeSelfFromChannel(long lobbyId, String bearerToken) {
        return super.removeSelfFromChannel(lobbyId, bearerToken);
    }

    @Override
    public void deleteAllTransientResources() {
        super.deleteAllTransientResources();
    }
}
