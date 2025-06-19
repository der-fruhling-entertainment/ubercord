package net.derfruhling.minecraft.ubercord;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public non-sealed class CustomManagedChannelService extends ManagedChannelService {
    private final @Nullable String botToken;
    private final Map<Long, List<Long>> memberTrackers = new HashMap<>();

    private static final String PLACEHOLDER_TOKEN = "$custom-in-use$";
    private static final int CAN_LINK = 1;

    private record DiscordCreateLobbyRequest(
            @Nullable
            Map<String, String> metadata,

            @SerializedName("idle_timeout_seconds")
            int idleTimeoutSeconds
    ) {}

    private record DiscordUpdateMemberRequest(
            @Nullable Map<String, String> metadata,
            int flags
    ) {}

    private record DiscordCreateLobbyResponse(
            long id
    ) {}

    public CustomManagedChannelService(@Nullable String botToken) {
        this.botToken = botToken;
    }

    @Override
    public CompletableFuture<OwnedChannel> createServerChannel(String name) throws ChannelExistsException {
        assert botToken != null;
        createOwnedChannelRequest(name);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("name", name);
        metadata.put("kind", "server");
        metadata.put("owner", "server-owned");
        metadata.put("ups-managed", "true"); // phony
        metadata.put("server-managed", "true");

        DiscordCreateLobbyRequest request = new DiscordCreateLobbyRequest(
            metadata,
            300 // dies after 5 minutes if not used
        );

        return http.sendAsync(HttpRequest.newBuilder()
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                        .header("User-Agent", "Ubercord (https://github.com/der-fruhling-entertainment/ubercord")
                        .header("Authorization", "Bot " + botToken)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .uri(URI.create("https://discord.com/api/v10/lobbies"))
                        .build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(stringHttpResponse -> {
                    if(stringHttpResponse.statusCode() != 201) {
                        throw new RuntimeException("Failed to create channel, HTTP " + stringHttpResponse.statusCode());
                    }

                    DiscordCreateLobbyResponse resp = gson.fromJson(stringHttpResponse.body(), DiscordCreateLobbyResponse.class);
                    return new OwnedChannel(resp.id, PLACEHOLDER_TOKEN, name, ManagedChannelKind.SERVER);
                })
                .thenApply(ownedChannel -> {
                    synchronized (CustomManagedChannelService.this) {
                        memberTrackers.put(ownedChannel.lobbyId(), new ArrayList<>());
                    }

                    completeOwnedChannelRequest(name, ownedChannel);
                    return ownedChannel;
                })
                .exceptionallyCompose((throwable) -> {
                    failOwnedChannelRequest(name);
                    return CompletableFuture.failedStage(throwable);
                });
    }

    @Override
    public CompletableFuture<OwnedChannel> createPersonalChannel(String name, String bearerToken) throws ChannelExistsException {
        throw new UnsupportedOperationException("Not supported with custom channel service");
    }

    @Override
    public CompletableFuture<Void> deleteChannel(OwnedChannel channel) {
        assert botToken != null;
        return http.sendAsync(HttpRequest.newBuilder()
                        .DELETE()
                        .header("User-Agent", "Ubercord (https://github.com/der-fruhling-entertainment/ubercord")
                        .header("Authorization", "Bot " + botToken)
                        .uri(URI.create("https://discord.com/api/v10/lobbies/" + channel.lobbyId()))
                        .build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept((stringHttpResponse) -> {
                    if(stringHttpResponse.statusCode() != 204) {
                        throw new RuntimeException("Failed to delete channel: HTTP " + stringHttpResponse.statusCode());
                    }

                    synchronized (CustomManagedChannelService.this) {
                        memberTrackers.remove(channel.lobbyId());
                        getOwnedChannels().remove(channel.name());
                    }
                });
    }

    @Override
    public CompletableFuture<String> requestPermissionsToken(long lobbyId, ManagedChannelKind expectedKind, String bearerToken) {
        if(getOwnedChannel(lobbyId) == null) throw new IllegalArgumentException("Lobby " + lobbyId + " does not exist");

        return CompletableFuture.completedFuture(PLACEHOLDER_TOKEN);
    }

    @Override
    public CompletableFuture<Void> addUserToChannel(OwnedChannel channel, long userId, String permissionsToken, boolean canLink) {
        assert botToken != null;
        return http.sendAsync(HttpRequest.newBuilder()
                        .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(new DiscordUpdateMemberRequest(null, canLink ? CAN_LINK : 0))))
                        .header("User-Agent", "Ubercord (https://github.com/der-fruhling-entertainment/ubercord")
                        .header("Authorization", "Bot " + botToken)
                        .header("Content-Type", "application/json")
                        .uri(URI.create("https://discord.com/api/v10/lobbies/" + channel.lobbyId() + "/members/" + userId))
                        .build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept((stringHttpResponse) -> {
                    if(stringHttpResponse.statusCode() != 200) {
                        throw new RuntimeException("Failed to add user to channel: HTTP " + stringHttpResponse.statusCode());
                    } else {
                        memberTrackers.get(channel.lobbyId()).add(userId);
                    }
                });
    }

    @Override
    public CompletableFuture<Void> removeUserFromChannel(OwnedChannel channel, long userId) {
        assert botToken != null;
        return http.sendAsync(HttpRequest.newBuilder()
                        .DELETE()
                        .header("User-Agent", "Ubercord (https://github.com/der-fruhling-entertainment/ubercord")
                        .header("Authorization", "Bot " + botToken)
                        .uri(URI.create("https://discord.com/api/v10/lobbies/" + channel.lobbyId() + "/members/" + userId))
                        .build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept((stringHttpResponse) -> {
                    if(stringHttpResponse.statusCode() != 204) {
                        throw new RuntimeException("Failed to remove user from channel: HTTP " + stringHttpResponse.statusCode());
                    } else {
                        memberTrackers.get(channel.lobbyId()).remove(userId);
                    }
                });
    }

    @Override
    public CompletableFuture<Void> removeSelfFromChannel(long lobbyId, String bearerToken) {
        throw new UnsupportedOperationException("Not supported with custom channel service");
    }

    @Override
    public void deleteAllTransientResources() {
        super.deleteAllTransientResources();
    }

    public Stream<OwnedChannel> getOwnedChannelsContaining(long userId) {
        return memberTrackers
                .entrySet().stream()
                .filter(v -> v.getValue().contains(userId))
                .map(v -> getOwnedChannel(v.getKey()))
                .filter(Objects::nonNull);
    }
}
