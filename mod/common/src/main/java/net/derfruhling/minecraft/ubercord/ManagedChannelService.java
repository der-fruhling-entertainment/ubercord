package net.derfruhling.minecraft.ubercord;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public sealed class ManagedChannelService permits CustomManagedChannelService {
    protected final HttpClient http = HttpClient.newBuilder().build();
    protected final Gson gson = new Gson();
    private final HashMap<String, OwnedChannel> ownedChannels = new HashMap<>();
    private final HashMap<Long, OwnedChannel> ownedChannelsById = new HashMap<>();
    private final List<String> reservedChannelNames = new ArrayList<>();

    public static class ServerChannelConfig {
        private boolean isServerDefault = false;

        public void setServerDefault(boolean serverDefault) {
            isServerDefault = serverDefault;
        }

        public Map<String, String> build() {
            Map<String, String> map = new HashMap<>();
            if(isServerDefault) map.put("server-default", "true");
            return map;
        }
    }

    public ManagedChannelService() {
    }

    protected record CreateChannelRequest(
            String name,
            ManagedChannelKind kind,
            Map<String, String> meta
    ) {}

    protected synchronized void createOwnedChannelRequest(String name) throws ChannelExistsException {
        if(ownedChannels.containsKey(name)) {
            throw new ChannelExistsException("Channel with name " + name + " exists and is owned by this client already");
        }

        if(reservedChannelNames.contains(name)) {
            throw new ChannelExistsException("Channel with name " + name + " is still in the process of being created");
        }

        reservedChannelNames.add(name);
    }

    public CompletableFuture<OwnedChannel> createServerChannel(String name) throws ChannelExistsException {
        return createServerChannel(name, new ServerChannelConfig());
    }

    public CompletableFuture<OwnedChannel> createServerChannel(String name, @NotNull ServerChannelConfig config) throws ChannelExistsException {
        CreateChannelRequest request = new CreateChannelRequest(
                name,
                ManagedChannelKind.SERVER,
                config.build()
        );

        createOwnedChannelRequest(name);

        return http.sendAsync(HttpRequest.newBuilder()
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                        .header("User-Agent", "Ubercord (https://github.com/der-fruhling-entertainment/ubercord")
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .uri(URI.create("https://ubercord.derfruhling.net/lobbies"))
                        .build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(stringHttpResponse -> {
                    if(stringHttpResponse.statusCode() != 200) {
                        throw new RuntimeException("Failed to create channel, HTTP " + stringHttpResponse.statusCode());
                    }

                    return gson.fromJson(stringHttpResponse.body(), OwnedChannel.class);
                })
                .thenApply(ownedChannel -> {
                    completeOwnedChannelRequest(name, ownedChannel);
                    return ownedChannel;
                })
                .exceptionallyCompose((throwable) -> {
                    failOwnedChannelRequest(name);
                    return CompletableFuture.failedStage(throwable);
                });
    }

    protected synchronized void failOwnedChannelRequest(String name) {
        reservedChannelNames.remove(name);
    }

    protected synchronized void completeOwnedChannelRequest(String name, OwnedChannel ownedChannel) {
        ownedChannels.put(name, ownedChannel);
        ownedChannelsById.put(ownedChannel.lobbyId(), ownedChannel);
        reservedChannelNames.remove(name);
    }

    public CompletableFuture<OwnedChannel> createPersonalChannel(String name, String bearerToken) throws ChannelExistsException {
        CreateChannelRequest request = new CreateChannelRequest(
                name,
                ManagedChannelKind.PERSONAL,
                Collections.emptyMap()
        );

        createOwnedChannelRequest(name);

        return http.sendAsync(HttpRequest.newBuilder()
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)))
                        .header("User-Agent", "Ubercord (https://github.com/der-fruhling-entertainment/ubercord")
                        .header("X-BearerToken", bearerToken) // required for PERSONAL channels
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .uri(URI.create("https://ubercord.derfruhling.net/lobbies"))
                        .build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(stringHttpResponse -> {
                    if(stringHttpResponse.statusCode() != 200) {
                        throw new RuntimeException("Failed to create channel, HTTP " + stringHttpResponse.statusCode());
                    }

                    return gson.fromJson(stringHttpResponse.body(), OwnedChannel.class);
                })
                .thenApply(ownedChannel -> {
                    completeOwnedChannelRequest(name, ownedChannel);
                    return ownedChannel;
                })
                .exceptionallyCompose((throwable) -> {
                    failOwnedChannelRequest(request.name);
                    return CompletableFuture.failedStage(throwable);
                });
    }

    public CompletableFuture<Void> deleteChannel(OwnedChannel channel) {
        return http.sendAsync(HttpRequest.newBuilder()
                        .DELETE()
                        .header("User-Agent", "Ubercord (https://github.com/der-fruhling-entertainment/ubercord")
                        .header("X-LobbyToken", channel.lobbyToken())
                        .uri(URI.create("https://ubercord.derfruhling.net/lobbies/" + channel.lobbyId()))
                        .build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept((stringHttpResponse) -> {
                    if(stringHttpResponse.statusCode() != 200) {
                        throw new RuntimeException("Failed to delete channel: HTTP " + stringHttpResponse.statusCode());
                    }

                    synchronized (ManagedChannelService.this) {
                        ownedChannels.remove(channel.name());
                    }
                });
    }

    public CompletableFuture<String> requestPermissionsToken(long lobbyId, ManagedChannelKind expectedKind, String bearerToken) {
        return http.sendAsync(HttpRequest.newBuilder()
                        .GET()
                        .header("User-Agent", "Ubercord (https://github.com/der-fruhling-entertainment/ubercord")
                        .header("X-BearerToken", bearerToken)
                        .uri(URI.create("https://ubercord.derfruhling.net/lobbies/" + lobbyId + "/permission_token?what=" + expectedKind.name()))
                        .build(), HttpResponse.BodyHandlers.ofString())
                .thenApply((stringHttpResponse) -> {
                    if(stringHttpResponse.statusCode() != 200) {
                        throw new RuntimeException("Failed to request channel join permission token: HTTP " + stringHttpResponse.statusCode());
                    }

                    return stringHttpResponse.body();
                });
    }

    private record AddMemberRequest(boolean canLink) {}

    public CompletableFuture<Void> addUserToChannel(OwnedChannel channel, long userId, String permissionsToken) {
        return addUserToChannel(channel, userId, permissionsToken, false);
    }

    public CompletableFuture<Void> addUserToChannel(OwnedChannel channel, long userId, String permissionsToken, boolean canLink) {
        return http.sendAsync(HttpRequest.newBuilder()
                        .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(new AddMemberRequest(canLink))))
                        .header("User-Agent", "Ubercord (https://github.com/der-fruhling-entertainment/ubercord")
                        .header("X-LobbyToken", channel.lobbyToken())
                        .header("X-PermissionToken", permissionsToken)
                        .header("Content-Type", "application/json")
                        .uri(URI.create("https://ubercord.derfruhling.net/lobbies/" + channel.lobbyId() + "/members/" + userId))
                        .build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept((stringHttpResponse) -> {
                    if(stringHttpResponse.statusCode() != 200) {
                        throw new RuntimeException("Failed to add user to channel: HTTP " + stringHttpResponse.statusCode());
                    }
                });
    }

    public CompletableFuture<Void> removeUserFromChannel(OwnedChannel channel, long userId) {
        return http.sendAsync(HttpRequest.newBuilder()
                        .DELETE()
                        .header("User-Agent", "Ubercord (https://github.com/der-fruhling-entertainment/ubercord")
                        .header("X-LobbyToken", channel.lobbyToken())
                        .header("Content-Type", "application/json")
                        .uri(URI.create("https://ubercord.derfruhling.net/lobbies/" + channel.lobbyId() + "/members/" + userId))
                        .build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept((stringHttpResponse) -> {
                    if(stringHttpResponse.statusCode() != 200) {
                        throw new RuntimeException("Failed to remove user from channel: HTTP " + stringHttpResponse.statusCode());
                    }
                });
    }

    public CompletableFuture<Void> removeSelfFromChannel(long lobbyId, String bearerToken) {
        return http.sendAsync(HttpRequest.newBuilder()
                        .DELETE()
                        .header("User-Agent", "Ubercord (https://github.com/der-fruhling-entertainment/ubercord")
                        .header("X-BearerToken", bearerToken)
                        .header("Content-Type", "application/json")
                        .uri(URI.create("https://ubercord.derfruhling.net/lobbies/" + lobbyId + "/members/@me"))
                        .build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept((stringHttpResponse) -> {
                    if(stringHttpResponse.statusCode() != 200) {
                        throw new RuntimeException("Failed to remove self from channel: HTTP " + stringHttpResponse.statusCode());
                    }
                });
    }

    public void deleteAllTransientResources() {
        // Not used
    }

    public HashMap<String, OwnedChannel> getOwnedChannels() {
        return ownedChannels;
    }

    public @Nullable OwnedChannel getOwnedChannel(String name) {
        return ownedChannels.get(name);
    }

    public @Nullable OwnedChannel getOwnedChannel(long lobbyId) {
        return ownedChannelsById.get(lobbyId);
    }
}
