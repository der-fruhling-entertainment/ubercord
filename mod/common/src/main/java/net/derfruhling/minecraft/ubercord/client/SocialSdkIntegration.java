package net.derfruhling.minecraft.ubercord.client;


import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import dev.architectury.networking.NetworkManager;
import net.derfruhling.discord.socialsdk4j.*;
import net.derfruhling.minecraft.ubercord.DisplayConfig;
import net.derfruhling.minecraft.ubercord.DisplayMode;
import net.derfruhling.minecraft.ubercord.JoinSecret;
import net.derfruhling.minecraft.ubercord.gui.DiesOnChannelChange;
import net.derfruhling.minecraft.ubercord.gui.SuddenInviteScreen;
import net.derfruhling.minecraft.ubercord.gui.SuddenJoinRequestScreen;
import net.derfruhling.minecraft.ubercord.packets.BeginProvisionalAuthorizationFlow;
import net.derfruhling.minecraft.ubercord.packets.NotifyAboutUserId;
import net.derfruhling.minecraft.ubercord.packets.SetUserIdPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;

public class SocialSdkIntegration {
    public static final long BUILTIN_CLIENT_ID = 1367390201621512313L;

    private static final Logger log = LogManager.getLogger(SocialSdkIntegration.class);

    private record Cache(String accessToken, String refreshToken, boolean isProvisional) {}
    
    private final Client client = new Client();

    private @Nullable Lobby currentLobby = null;
    private Map<String, Lobby> knownLobbies = new HashMap<>();
    public User self;
    private ClientConfig config = ClientConfig.loadMaybe();
    private final VolatileClientConfig userPrefs = VolatileClientConfig.loadMaybe();
    private final Map<Long, MessageRef> refs = new HashMap<>();
    private final HttpClient http = HttpClient.newHttpClient();
    private final Map<String, OnlinePlayer> onlinePlayers = new HashMap<>();
    private final Map<Long, OnlinePlayer> onlinePlayersById = new HashMap<>();
    private final Map<Long, ActivityInvite> invites = new HashMap<>();
    private DisplayConfig defaultConfig = config.getDefaultConfig();
    private DisplayConfig displayConfig = defaultConfig;

    private final long start = Instant.now().getEpochSecond() * 1000;

    public ClientConfig getConfig() {
        return config;
    }

    public void setConfig(ClientConfig config) {
        this.config = config;

        if(this.displayConfig == this.defaultConfig) {
            this.displayConfig = config.getDefaultConfig();
        }

        this.defaultConfig = config.getDefaultConfig();
    }

    private record OnlinePlayer(String username, long userId, boolean isProvisional) {}

    public final long defaultClientId = config.getDefaultClientId() == 0 ? BUILTIN_CLIENT_ID : config.getDefaultClientId();

    private static final AtomicBoolean isReady = new AtomicBoolean(false);
    private static final List<Runnable> whenReady = new ArrayList<>();

    public SocialSdkIntegration() {
        client.setStatusChangedCallback((status, error, errorDetail) -> {
            synchronized (this) {
                log.info("Received status changed event, now {}", status);

                if(status == Client.Status.Ready) {
                    self = client.getUser();
                    isReady.set(true);

                    if(chatFeaturesEnabled) {
                        generatePrebuiltMessage(Badge.DISCORD, Component.translatable("ubercord.auth.connected"));
                        NetworkManager.sendToServer(new SetUserIdPacket(self.id, self.isProvisional()));
                    }

                    log.info("Executing {} deferred runnables", whenReady.size());
                    whenReady.forEach(Runnable::run);
                    whenReady.clear();
                } else if (isReady.get()) {
                    if(chatFeaturesEnabled) {
                        generatePrebuiltMessage(Badge.YELLOW_EXCLAIM, Component.translatable("ubercord.auth.disconnected"));
                        generateChatAuthSelection(defaultClientId);
                        NetworkManager.sendToServer(SetUserIdPacket.DISCARD);
                    }

                    isReady.set(false);
                }
            }
        });

        client.setLobbyCreatedCallback(lobbyId -> {
            if(!chatFeaturesEnabled) {
                client.leaveLobby(lobbyId, result -> {
                    if(!result.isSuccess()) {
                        log.error("Failed to leave lobby {}: {}", lobbyId, result.message());
                    }
                });
                return;
            }

            Lobby lobby = client.getLobby(lobbyId);

            if(lobby != null) {
                var meta = lobby.getMetadata();

                if(meta.containsKey("channel-name")) {
                    synchronized (this) {
                        knownLobbies.put(meta.get("channel-name"), lobby);

                        if((currentLobby == null && userPrefs.preferredChannel == null) || (userPrefs.preferredChannel != null && userPrefs.preferredChannel.equals(meta.get("channel-name")))) {
                            currentLobby = lobby;

                            Minecraft.getInstance().execute(() -> {
                                String partyName = meta.get("channel-name");
                                generatePartyJoinMessage(partyName);
                                generateChannelSwitchMessage(partyName);
                            });
                        } else {
                            Minecraft.getInstance().execute(() -> {
                                String partyName = meta.get("channel-name");
                                generatePartyJoinMessage(partyName);
                            });
                        }
                    }
                } else {
                    log.error("Lobby {} has no channel name (not created by ubercord?)", lobbyId);
                }
            } else {
                log.error("Lobby {} is not valid?", lobbyId);
            }
        });

        client.setLobbyDeletedCallback(lobbyId -> {
            synchronized (this) {
                //noinspection unchecked
                for (Map.Entry<String, Lobby> lobby : knownLobbies.entrySet().toArray(Map.Entry[]::new)) {
                    if(lobby.getValue().id == lobbyId) {
                        knownLobbies.remove(lobby.getKey());

                        Minecraft.getInstance().execute(() -> {
                            generatePartyLeaveMessage(lobby.getKey());
                        });
                    }
                }
            }
        });

        client.setMessageCreatedCallback(messageId -> {
            if(refs.containsKey(messageId) || !chatFeaturesEnabled) return;

            Message msg = Objects.requireNonNull(client.getMessage(messageId));
            @Nullable User author = msg.getAuthor();
            @Nullable User recipient = msg.getRecipient();

            String authorName;
            Badge authorBadge;
            Channel channel = msg.getChannel();

            if(author != null && onlinePlayersById.containsKey(author.id) && msg.isSentFromGame()) {
                authorName = author.getDisplayName();
                authorBadge = Badge.ONLINE_USER;
            } else {
                if(author != null) {
                    authorName = author.getDisplayName();
                    authorBadge = author.getUsername().equals("discord") ? Badge.RED_EXCLAIM : Badge.OFFLINE_USER;
                } else {
                    authorName = "<webhook>";
                    authorBadge = Badge.OFFLINE_USER;
                }
            }

            if(msg.getDisclosureType() != null) {
                switch (msg.getDisclosureType()) {
                    case MessageDataVisibleOnDiscord -> generatePrebuiltMessage(Badge.RED_EXCLAIM, Component.translatable("ubercord.disclosure.message_data").withColor(0xFFFFFFFF));
                }
            } else {
                if(channel != null && channel.type().isDm() && recipient != null) {
                    Badge recipientBadge = onlinePlayersById.containsKey(recipient.id)
                            ? Badge.ONLINE_USER
                            : Badge.OFFLINE_USER;
                    OnlinePlayer ign = onlinePlayersById.get(msg.getAuthorId());
                    Relationship relationship = client.getRelationship(msg.getRecipientId());
                    if(self.id == msg.getAuthorId()) {
                        generateSentDmMessage(messageId, ign != null ? ign.username : null, recipient.getDisplayName(), recipientBadge, relationship, recipient.id, msg.getContent());
                    } else {
                        generateDmMessage(messageId, ign != null ? ign.username : null, recipient.getDisplayName(), recipientBadge, relationship, recipient.id, msg.getContent());
                    }
                } else {
                    Lobby lobby = msg.getLobby();
                    if(lobby == null) return;

                    Relationship relationship = client.getRelationship(msg.getAuthorId());
                    OnlinePlayer ign = onlinePlayersById.get(msg.getAuthorId());
                    receiveMessage(lobby.id, ign != null ? ign.username : null, authorName, msg.getAuthorId(), authorBadge, relationship, msg.getContent(), messageId);
                }
            }
        });

        client.setMessageUpdatedCallback(messageId -> {
            if(!chatFeaturesEnabled) return;

            Message msg = Objects.requireNonNull(client.getMessage(messageId));

            if(refs.containsKey(msg.id)) {
                refs.get(msg.id).edit(orig -> Component.literal(msg.getContent()).withStyle(ChatFormatting.WHITE));
            }
        });

        client.setMessageDeletedCallback((messageId, channelId) -> {
            if(!chatFeaturesEnabled) return;

            if(refs.containsKey(messageId)) {
                refs.get(messageId).onDeleted();
            }
        });

        client.setRelationshipCreatedCallback((userId, discordRelationship) -> {
            if(!chatFeaturesEnabled) return;

            if(discordRelationship) {
                switch(client.getRelationship(userId).discordType()) {
                    case PendingIncoming -> {
                        User user = client.getUser(userId);
                        assert user != null;
                        generatePrebuiltMessage(
                                Badge.YELLOW_EXCLAIM,
                                Component.translatable("ubercord.notification.friend_request.new.discord", user.getDisplayName())
                                        .append("\n\n")
                                        .append(Component.translatable("ubercord.notification.friend_request.new.accept")
                                                .withStyle(Style.EMPTY
                                                        .withColor(ChatFormatting.GREEN)
                                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl discord-friends accept " + userId))))
                                        .append(" ")
                                        .append(Component.translatable("ubercord.notification.friend_request.new.reject")
                                                .withStyle(Style.EMPTY
                                                        .withColor(ChatFormatting.RED)
                                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl discord-friends reject " + userId))))
                        );
                    }

                    case Friend -> {
                        User user = client.getUser(userId);
                        assert user != null;
                        generateToast(
                                Component.translatable("ubercord.toasts.friends.new.discord"),
                                Component.literal(user.getDisplayName())
                        );
                    }
                }
            } else {
                switch(client.getRelationship(userId).gameType()) {
                    case PendingIncoming -> {
                        User user = client.getUser(userId);
                        assert user != null;
                        generatePrebuiltMessage(
                                Badge.YELLOW_EXCLAIM,
                                Component.translatable("ubercord.notification.friend_request.new.game", user.getDisplayName())
                                        .append("\n\n")
                                        .append(Component.translatable("ubercord.notification.friend_request.new.accept")
                                                .withStyle(Style.EMPTY
                                                        .withColor(ChatFormatting.GREEN)
                                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl game-friends accept " + userId))))
                                        .append(" ")
                                        .append(Component.translatable("ubercord.notification.friend_request.new.reject")
                                                .withStyle(Style.EMPTY
                                                        .withColor(ChatFormatting.RED)
                                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl game-friends reject " + userId))))
                        );
                    }

                    case Friend -> {
                        User user = client.getUser(userId);
                        assert user != null;
                        generateToast(
                                Component.translatable("ubercord.toasts.friends.new.game"),
                                Component.literal(user.getDisplayName())
                        );
                    }
                }
            }
        });

        client.setActivityJoinCallback(SocialSdkIntegration::connectWithJoinSecret);

        client.setActivityInviteCreatedCallback(invite -> {
            if(invite.isValid()) {
                invites.put(invite.messageId(), invite);
            }

            switch(invite.type()) {
                case Join -> {
                    if(Minecraft.getInstance().player != null) {
                        User user = client.getUser(invite.senderId());
                        assert user != null;
                        generatePrebuiltMessage(Badge.YELLOW_DOT, Component.translatable("ubercord.invite.new", user.getDisplayName())
                                .append("\n\n")
                                .append(Component.translatable("ubercord.invite.accept")
                                        .withStyle(Style.EMPTY
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl invite accept " + invite.messageId()))
                                                .withColor(ChatFormatting.GREEN)))
                                .append(" ")
                                .append(Component.translatable("ubercord.invite.ignore")
                                        .withStyle(Style.EMPTY
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl invite ignore " + invite.messageId()))
                                                .withColor(ChatFormatting.GREEN))),
                                invite.messageId());
                    } else {
                        Minecraft.getInstance().setScreen(new SuddenInviteScreen(Minecraft.getInstance().screen, invite));
                    }
                }

                case JoinRequest -> {
                    if(Minecraft.getInstance().player != null) {
                        User user = client.getUser(invite.senderId());
                        assert user != null;
                        generatePrebuiltMessage(Badge.YELLOW_DOT, Component.translatable("ubercord.invite.join_request", user.getDisplayName())
                                        .append("\n\n")
                                        .append(Component.translatable("ubercord.invite.accept")
                                                .withStyle(Style.EMPTY
                                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl invite accept " + invite.messageId()))
                                                        .withColor(ChatFormatting.GREEN)))
                                        .append(" ")
                                        .append(Component.translatable("ubercord.invite.ignore")
                                                .withStyle(Style.EMPTY
                                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl invite ignore " + invite.messageId()))
                                                        .withColor(ChatFormatting.GREEN))),
                                invite.messageId());
                    } else {
                        Minecraft.getInstance().setScreen(new SuddenJoinRequestScreen(Minecraft.getInstance().screen, invite));
                    }
                }
            }
        });

        client.setActivityInviteUpdatedCallback(invite -> {
            if(invite.isValid()) {
                invites.put(invite.messageId(), invite);
            } else {
                invites.remove(invite.messageId());
            }
        });
    }

    public static void connectWithJoinSecret(String joinSecret) {
        JoinSecret secret = JoinSecret.decode(joinSecret);
        log.info("Joining {}'s game: {}:{}", secret.inviter(), secret.ip(), secret.port());

        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if(conn != null && !Minecraft.getInstance().isSingleplayer()) {
            conn.handleTransfer(new ClientboundTransferPacket(secret.ip(), secret.port()));
        } else {
            ConnectScreen.startConnecting(
                    new JoinMultiplayerScreen(new TitleScreen()),
                    Minecraft.getInstance(),
                    ServerAddress.parseString(secret.ip() + ":" + secret.port()),
                    new ServerData("{}'s game", secret.ip(), ServerData.Type.OTHER),
                    false,
                    null
            );
        }
    }

    public boolean isReady() {
        return isReady.get();
    }

    public static SystemToast.SystemToastId SOCIAL_TOAST = new SystemToast.SystemToastId();

    public DisplayConfig getDisplayConfig() {
        return displayConfig;
    }

    public void disconnect() {
        synchronized (this) {
            knownLobbies.clear();
            currentLobby = null;
        }

        client.disconnect();
    }

    public Set<String> getOnlinePlayers() {
        return onlinePlayers.keySet();
    }

    public void updatePlayer(String username, long userId, boolean isProvisional) {
        OnlinePlayer player = new OnlinePlayer(username, userId, isProvisional);
        onlinePlayers.put(username, player);
        onlinePlayersById.put(userId, player);
    }

    public void removePlayer(String username) {
        OnlinePlayer player = onlinePlayers.remove(username);
        if(player == null) return;

        onlinePlayersById.remove(player.userId);
    }

    public void updatePlayer(NotifyAboutUserId packet) {
        if(packet.userId() == 0) {
            removePlayer(packet.username());
        } else {
            updatePlayer(packet.username(), packet.userId(), packet.isProvisional());
        }
    }

    public CompletableFuture<ClientResult> sendGameFriendRequest(String username) {
        OnlinePlayer player = onlinePlayers.get(username);
        CompletableFuture<ClientResult> future = new CompletableFuture<>();

        if(player != null) {
            client.sendGameFriendRequest(player.userId, future::complete);
        } else {
            future.completeExceptionally(new IllegalStateException("Cannot send game-friend request to " + username + " because either they or the server are not online and using Ubercord"));
        }

        return future;
    }

    public CompletableFuture<ClientResult> sendDiscordFriendRequest(String username) {
        OnlinePlayer player = onlinePlayers.get(username);
        CompletableFuture<ClientResult> future = new CompletableFuture<>();

        if(player != null) {
            client.sendDiscordFriendRequest(player.userId, future::complete);
        } else {
            future.completeExceptionally(new IllegalStateException("Cannot send game-friend request to " + username + " because either they or the server are not online and using Ubercord"));
        }

        return future;
    }

    public void connect() {
        connectWithClientId(defaultClientId, defaultClientId != BUILTIN_CLIENT_ID || ENABLE_CHAT_FEATURES_ON_DEFAULT_CLIENT);
    }

    public Iterable<Map.Entry<String, Lobby>> getJoinedChannels() {
        return knownLobbies.entrySet();
    }

    public Lobby getJoinedChannel(String name) {
        return knownLobbies.get(name);
    }

    public @Nullable Lobby getCurrentChannel() {
        return currentLobby;
    }

    private void setChannel(Lobby lobby) {
        currentLobby = lobby;

        Minecraft.getInstance().execute(() -> {
            Map<String, String> meta = lobby.getMetadata();
            generateChannelSwitchMessage(meta.get("channel-name"));

            if(Minecraft.getInstance().screen instanceof DiesOnChannelChange) {
                Minecraft.getInstance().setScreen(null);
            }
        });
    }

    public void setChannel(String channel) {
        setChannel(getJoinedChannel(channel));

        synchronized (this) {
            try {
                userPrefs.setPreferredChannel(channel);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private long currentClientId = 0;
    private boolean chatFeaturesEnabled = true;

    public long getCurrentClientId() {
        return currentClientId;
    }

    public static final boolean ENABLE_CHAT_FEATURES_ON_DEFAULT_CLIENT = true;

    public boolean isChatFeaturesEnabled() {
        return chatFeaturesEnabled;
    }

    public synchronized void connectWithClientId(long clientId, boolean chatFeaturesEnabled) {
        if(currentClientId == clientId) return;

        knownLobbies.clear();

        File cacheDir = new File(Minecraft.getInstance().gameDirectory, "ubercord");
        File cacheFile = new File(cacheDir, "social-sdk-" + clientId + ".json");

        if(chatFeaturesEnabled) {
            if(cacheFile.exists()) {
                try {
                    String text = Files.readString(cacheFile.toPath());
                    Cache cache = new Gson().fromJson(text, Cache.class);

                    if(!cache.isProvisional) {
                        client.updateToken(AuthorizationTokenType.Bearer, cache.accessToken, result -> {
                            if (result.isSuccess()) {
                                client.connect();

                                this.currentClientId = clientId;
                                this.chatFeaturesEnabled = true;
                            } else {
                                authorizeReal(clientId, true);
                            }
                        });
                    } else {
                        authorizeProvisional(clientId);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                generateChatAuthSelection(clientId);
            }
        } else {
            if(cacheFile.exists()) {
                try {
                    String text = Files.readString(cacheFile.toPath());
                    Cache cache = new Gson().fromJson(text, Cache.class);

                    if(!cache.isProvisional) {
                        client.updateToken(AuthorizationTokenType.Bearer, cache.accessToken, result -> {
                            if (result.isSuccess()) {
                                client.connect();

                                this.currentClientId = clientId;
                                this.chatFeaturesEnabled = false;
                            } else {
                                authorizeReal(clientId, false);
                            }
                        });
                    } else {
                        // provisional accounts not useful without chat features
                        authorizeReal(clientId, false);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                authorizeReal(clientId, false);
            }
        }
    }

    public void generateToast(Component title, @Nullable Component content) {
        Minecraft.getInstance().getToasts().addToast(new SystemToast(SOCIAL_TOAST, title, content));
    }

    private static void generateChatAuthSelection(long clientId) {
        Minecraft.getInstance().gui.getChat().addMessage(Component
                .literal("To use chat here, select an option below:\n")
                .append(Component.literal("[Connect with a Discord account]\n")
                        .withStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl authorize discord " + clientId))
                                .withColor(0x5865F2)
                                .withUnderlined(true)))
                .append(Component.literal("[Just use your Minecraft name]")
                        .withStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl authorize provisional " + clientId))
                                .withColor(ChatFormatting.GRAY)
                                .withUnderlined(true)))
        );
    }

    // TODO use provisional merge here as the docs say (keeps it unique with provisional accts)
    void authorizeReal(long clientId, boolean chatFeaturesEnabled) {
        String state = Minecraft.getInstance().getUser().getName();
        CodeVerifier ver = client.createAuthorizationCodeVerifier();

        generatePrebuiltMessage(Badge.YELLOW_DOT, Component.translatable("ubercord.auth.discord.begin"));
        client.authorize(clientId, chatFeaturesEnabled ? Client.COMMUNICATIONS_SCOPES : Client.PRESENCE_SCOPES, state, ver.challenge(), (result, code, redirectUri) -> {
            if (result.isSuccess()) {
                client.getToken(clientId, code, ver.verifier(), redirectUri, (result1, accessToken, refreshToken, type, expiresIn, scopes) -> {
                    if (result1.isSuccess()) {
                        onTokenGet(clientId, accessToken, refreshToken, false);
                    } else {
                        generatePrebuiltMessage(Badge.RED_EXCLAIM, Component.translatable("ubercord.auth.discord.failed_token", result1.message()));
                        authorizeProvisional(clientId);
                    }
                });
            } else {
                generatePrebuiltMessage(Badge.RED_EXCLAIM, Component.translatable("ubercord.auth.discord.failed_auth", result.message()));
                authorizeProvisional(clientId);
            }
        });
    }

    private void onTokenGet(long clientId, String accessToken, String refreshToken, boolean isProvisional) {
        generatePrebuiltMessage(Badge.YELLOW_DOT, Component.translatable("ubercord.auth.authorization_successful"));
        Cache cache = new Cache(accessToken, refreshToken, isProvisional);

        File cacheDir = new File(Minecraft.getInstance().gameDirectory, "ubercord");
        if(!cacheDir.exists()) cacheDir.mkdirs();
        File cacheFile = new File(cacheDir, "social-sdk-" + clientId + ".json");

        try {
            Files.writeString(cacheFile.toPath(), new Gson().toJson(cache), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        client.updateToken(AuthorizationTokenType.Bearer, cache.accessToken, result2 -> {
            if (result2.isSuccess()) {
                generatePrebuiltMessage(Badge.YELLOW_DOT, Component.translatable("ubercord.auth.connecting"));
                client.connect();
            } else {
                generatePrebuiltMessage(Badge.RED_EXCLAIM, Component.translatable("ubercord.auth.update_token_error", result2.message()));
                if(!isProvisional) authorizeProvisional(clientId);
            }
        });
    }

    private AtomicBoolean isWaitingForProvisional = new AtomicBoolean(false);
    private AtomicReference<String> provisionalState = new AtomicReference<>();
    private long clientId = 0;

    void authorizeProvisional(long clientId) {
        generatePrebuiltMessage(Badge.YELLOW_DOT, Component.translatable("ubercord.auth.provisional.begin"));
        isWaitingForProvisional.set(true);
        provisionalState.set(clientId + ":" + new Random().nextLong());
        NetworkManager.sendToServer(new BeginProvisionalAuthorizationFlow(provisionalState.get()));
        this.clientId = clientId;
    }

    private record ProvisionalPostRequest(
            UUID id,
            String username,
            @SerializedName("server_secret")
            String serverSecret
    ) {}

    private static class OfflineException extends RuntimeException {
        public OfflineException(String message) {
            super(message);
        }
    }

    void completeProvisionalExchange(String secret) {
        if(!isWaitingForProvisional.get()) return;

        isWaitingForProvisional.set(false);
        generatePrebuiltMessage(Badge.YELLOW_DOT, Component.translatable("ubercord.auth.provisional.got_token"));
        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;
        try {
            http.sendAsync(HttpRequest.newBuilder()
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    new Gson().toJson(new ProvisionalPostRequest(
                                            player.getUUID(),
                                            player.getName().getString(),
                                            secret
                                    ))
                            ))
                            .uri(URI.create("https://maximum-honest-cat.ngrok-free.app/auth-client"))
                            .build(), HttpResponse.BodyHandlers.ofString())
                    .handleAsync((response, throwable) -> {
                        if(throwable != null) {
                            generatePrebuiltMessage(Badge.YELLOW_DOT, Component.translatable("ubercord.auth.provisional.failed_throwable", throwable.getMessage()));
                        } else {
                            client.getProvisionalToken(clientId, ExternalAuthType.OIDC, response.body(), (result, accessToken, refreshToken, type, expiresIn, scopes) -> {
                                if (result.isSuccess()) {
                                    onTokenGet(clientId, accessToken, refreshToken, true);
                                } else {
                                    generatePrebuiltMessage(Badge.YELLOW_DOT, Component.translatable("ubercord.auth.provisional.failed_token", result.message()));
                                    generateChatAuthSelection(clientId);
                                }
                            });
                        }

                        return null;
                    });
        } catch (OfflineException e) {
            generatePrebuiltMessage(Badge.RED_EXCLAIM, Component.translatable("ubercord.auth.provisional.offline_mode_unsupported"));
        }
    }

    public Client getClient() {
        return client;
    }

    public void sendMessage(String message) {
        if(Minecraft.getInstance().player == null) return;

        if(currentLobby == null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("No channel selected. Use /channel <name> to change channels."));
        } else if(isReady.get()) {
            reallySendMessage(currentLobby.id, message);
        }
    }

    private void sendMessage(long lobbyId, String message) {
        if(Minecraft.getInstance().player == null) return;

        if(isReady.get()) {
            reallySendMessage(lobbyId, message);
        }
    }

    public void sendMessage(String channel, String message) {
        Lobby joinedChannel = getJoinedChannel(channel);

        if(joinedChannel == null) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Unknown channel: " + channel).withStyle(ChatFormatting.RED));
            }

            return;
        }

        sendMessage(joinedChannel.id, message);
    }

    private void reallySendMessage(long lobbyId, String message) {
        client.sendLobbyMessage(lobbyId, message, (result, messageId) -> {
            if(Minecraft.getInstance().player == null) return;

            if(result.isSuccess()) {
                Lobby lobby = client.getLobby(lobbyId);
                if(lobby != null && lobby.getLinkedChannel() == null) {
                    String ign = Minecraft.getInstance().getGameProfile().getName();
                    receiveMessage(lobbyId, ign, self.getDisplayName(), self.id, Badge.ONLINE_USER, null, message, messageId);
                }
            } else {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Error! " + result.message()));
            }
        });
    }

    private void receiveMessage(long lobbyId, @Nullable String ign, String username, long userId, Badge badge, @Nullable Relationship relationshipWithSource, String message, long messageId) {
        if(currentLobby != null && currentLobby.id == lobbyId) {
            generateMainLobbyMessage(messageId, ign, username, badge, relationshipWithSource, userId, message);
        } else {
            Lobby lobby = client.getLobby(lobbyId);
            if(lobby != null) {
                Map<String, String> meta = lobby.getMetadata();
                generateLobbyMessage(messageId, meta.get("channel-name"), ign, username, badge, relationshipWithSource, userId, message);
            }
        }
    }

    static class MessageRef {
        private final MutableComponent root;
        private MutableComponent message;
        private boolean hasBeenEdited = false;

        public MessageRef(MutableComponent root, MutableComponent message) {
            this.root = root;
            this.message = message;
        }

        public synchronized void edit(Function<MutableComponent, MutableComponent> editor) {
            if(!hasBeenEdited) {
                root.append(Component.literal(" (edited)").withStyle(ChatFormatting.DARK_GRAY));
                hasBeenEdited = true;
            }

            MutableComponent orig = message;
            message = editor.apply(message);

            if(orig != message) {
                root.getSiblings().set(root.getSiblings().indexOf(orig), message);

                Minecraft.getInstance().gui.getChat().rescaleChat();
            }
        }

        public void onDeleted() {
            if(hasBeenEdited) root.getSiblings().removeLast();
            root.append(Component.literal("(deleted)").withStyle(ChatFormatting.DARK_GRAY));
            root.getSiblings().remove(message);
            Minecraft.getInstance().gui.getChat().rescaleChat();
        }
    }

    public void generateDmMessage(
            long messageId,
            @Nullable String sourceIgn,
            String sourceUsername,
            Badge sourceBadge,
            @Nullable Relationship relationshipWithSource,
            long sourceUserId,
            String message
    ) {
        MutableComponent msg = Component.literal(message).withStyle(ChatFormatting.WHITE);
        MutableComponent root = Component.empty()
                .withStyle(ChatFormatting.GRAY)
                .append("[")
                .append(sourceBadge.create(sourceIgn, sourceUsername, sourceUserId, relationshipWithSource))
                .append(" → ")
                .append("you") // TODO localize
                .append("] ")
                .append(msg)
                .append("  ")
                .append(Badge.REPLY_BUTTON.create()
                        .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/dm " + sourceUserId + " "))));

        if(messageId != 0) {
            refs.put(messageId, new MessageRef(msg, root));
        }

        Minecraft.getInstance().gui.getChat().addMessage(
                root,
                null,
                null
        );
    }

    public void generateSentDmMessage(
            long messageId,
            @Nullable String targetIgn,
            String targetUsername,
            Badge targetBadge,
            @Nullable Relationship relationshipWithTarget,
            long targetUserId,
            String message
    ) {
        // TODO make new chat things
        MutableComponent msg = Component.literal(message).withStyle(ChatFormatting.WHITE);
        MutableComponent root = Component.empty()
                .withStyle(ChatFormatting.GRAY)
                .append("[")
                .append("you")
                .append(" → ")
                .append(targetBadge.create(targetIgn, targetUsername, targetUserId, relationshipWithTarget))
                .append("] ")
                .append(msg);

        if(messageId != 0) {
            refs.put(messageId, new MessageRef(msg, root));
        }

        Minecraft.getInstance().gui.getChat().addMessage(
                root,
                null,
                null
        );
    }

    public void generateLobbyMessage(
            long messageId,
            String lobbyName,
            @Nullable String sourceIgn,
            String sourceUsername,
            Badge sourceBadge,
            @Nullable Relationship relationshipWithSource,
            long sourceUserId,
            String message
    ) {
        MutableComponent msg = Component.literal(message).withStyle(ChatFormatting.WHITE);
        MutableComponent root = Component.empty()
                .withStyle(ChatFormatting.GRAY)
                .append("~")
                .append(Component.literal(lobbyName)
                        .withStyle(Style.EMPTY
                                .withColor(ChatFormatting.BLUE)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/channel " + lobbyName))))
                .append(" [")
                .append(sourceBadge.create(sourceIgn, sourceUsername, sourceUserId, relationshipWithSource))
                .append("] ")
                .append(msg);

        if(messageId != 0) {
            refs.put(messageId, new MessageRef(root, msg));
        }

        Minecraft.getInstance().gui.getChat().addMessage(
                root,
                null,
                null
        );
    }

    public void generateMainLobbyMessage(
            long messageId,
            @Nullable String sourceIgn,
            String sourceUsername,
            Badge sourceBadge,
            @Nullable Relationship relationshipWithSource,
            long sourceUserId,
            String message
    ) {
        MutableComponent msg = Component.literal(message).withStyle(ChatFormatting.WHITE);
        MutableComponent root = Component.empty()
                .withStyle(ChatFormatting.GRAY)
                .append("[")
                .append(sourceBadge.create(sourceIgn, sourceUsername, sourceUserId, relationshipWithSource))
                .append("] ")
                .append(msg);

        if(messageId != 0) {
            refs.put(messageId, new MessageRef(root, msg));
        }

        Minecraft.getInstance().gui.getChat().addMessage(
                root,
                null,
                null
        );
    }

    public void generatePrebuiltMessage(
            Badge sourceBadge,
            Component component,
            long messageId
    ) {
        MutableComponent msg = component.copy();
        MutableComponent root = Component.empty()
                .append(sourceBadge.create())
                .append(" ")
                .append(msg);

        if(messageId != 0) {
            refs.put(messageId, new MessageRef(root, msg));
        }

        Minecraft.getInstance().gui.getChat().addMessage(
                root,
                null,
                Minecraft.getInstance().isSingleplayer()
                        ? GuiMessageTag.system()
                        : GuiMessageTag.systemSinglePlayer()
        );
    }

    public void generatePrebuiltMessage(
            Badge sourceBadge,
            Component component
    ) {
        MutableComponent root = Component.empty()
                .append(sourceBadge.create())
                .append(" ")
                .append(component);

        Minecraft.getInstance().gui.getChat().addMessage(
                root,
                null,
                Minecraft.getInstance().isSingleplayer()
                        ? GuiMessageTag.system()
                        : GuiMessageTag.systemSinglePlayer()
        );
    }

    public void generatePartyJoinMessage(
            String partyName
    ) {
        generatePrebuiltMessage(Badge.YELLOW_DOT, Component.translatable("ubercord.party.join", partyName));
    }

    public void generatePartyLeaveMessage(
            String partyName
    ) {
        generatePrebuiltMessage(Badge.YELLOW_DOT, Component.translatable("ubercord.party.leave", partyName));
    }

    public void generateChannelSwitchMessage(
            String partyName
    ) {
        Minecraft.getInstance().getChatListener().handleSystemMessage(
                Component.empty()
                        .withStyle(ChatFormatting.GRAY)
                        .append("\n--- Channel ")
                        .append(partyName)
                        .append(" ---"),
                false
        );
    }

    public void updateRichPresence(DisplayMode mode, boolean allowJoinIfPossible) {
        if(isReady.get()) {
            reallyUpdateRichPresence(mode, allowJoinIfPossible);
        } else {
            whenReady.add(() -> reallyUpdateRichPresence(mode, allowJoinIfPossible));
        }
    }

    private void reallyUpdateRichPresence(DisplayMode mode, boolean allowJoinIfPossible) {
        Activity activity = new Activity()
                .setType(mode.type())
                .setState(substitute(mode.state()))
                .setDetails(substitute(mode.details()));

        if(mode.assets() != null) {
            String largeImage = null, largeText = null, smallImage = null, smallText = null;

            if(mode.assets().large() != null) {
                largeImage = substitute(mode.assets().large().image()).replaceAll("[:$#@!%^&*(){}\\[\\]<>?/,.;'\\\\|\"\\-+=]", "_");
                largeText = substitute(mode.assets().large().text());
            }

            if(mode.assets().small() != null) {
                smallImage = substitute(mode.assets().small().image()).replaceAll("[:$#@!%^&*(){}\\[\\]<>?/,.;'\\\\|\"\\-+=]", "_");
                smallText = substitute(mode.assets().small().text());
            }

            activity.setAssets(new Activity.Assets(
                    largeImage != null ? new Activity.Asset(largeImage, largeText) : null,
                    smallImage != null ? new Activity.Asset(smallImage, smallText) : null
            ));
        }

        activity.setName(mode.name());

        ServerData currentServer = Minecraft.getInstance().getCurrentServer();
        IntegratedServer integratedServer = Minecraft.getInstance().getSingleplayerServer();
        if(allowJoinIfPossible && currentServer != null || (integratedServer != null && integratedServer.isPublished())) {
            ClientPacketListener conn = Objects.requireNonNull(Minecraft.getInstance().getConnection());
            Collection<PlayerInfo> playerInfo = conn.getOnlinePlayers();

            if(currentServer != null) {
                ServerAddress addr = ServerAddress.parseString(currentServer.ip);
                StringBuilder sha = new StringBuilder();

                try {
                    byte[] bytes = MessageDigest.getInstance("SHA-256").digest((addr.getHost() + ":" + addr.getPort()).getBytes(StandardCharsets.UTF_8));

                    for (byte b : bytes) {
                        sha.append(String.format("%02x", Byte.toUnsignedInt(b)));
                    }
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }

                activity.setParty(sha.toString(), Math.max(playerInfo.size(), 1), currentServer.players == null ? 0 : currentServer.players.max(), true);
                activity.setJoinSecret(new JoinSecret(Minecraft.getInstance().getGameProfile().getName(), addr.getHost(), addr.getPort()).encode());
            } else {
                activity.setParty(":singleplayer", Math.max(playerInfo.size(), 1), integratedServer.getPlayerList().getMaxPlayers(), true);
            }
        }

        client.updateRichPresence(activity, result -> {
            if(!result.isSuccess()) {
                log.error("Failed to set rich presence: {}", result.message());
            }
        });
    }

    private static final Pattern SUB_PATTERN = Pattern.compile("%([a-z_]+)%");

    @Contract("!null -> new; null -> null")
    private @Nullable String substitute(@Nullable String value) {
        if(value == null) return null;

        return SUB_PATTERN.matcher(value).replaceAll(matchResult -> {
            String group = matchResult.group(1);
            return switch(group) {
                case "dimension_id" -> Minecraft.getInstance().player != null
                        ? Minecraft.getInstance().player.level().dimension().location().toString()
                        : "%" + group + "%";
                case "dimension" -> Minecraft.getInstance().player != null
                        ? displayConfig.dimensionNames().get(Minecraft.getInstance().player.level().dimension().location())
                        : "%" + group + "%";
                case "world_name" -> Minecraft.getInstance().getSingleplayerServer() != null
                        ? Minecraft.getInstance().getSingleplayerServer().getWorldData().getLevelName()
                        : "%" + group + "%";
                case "player_name" -> Minecraft.getInstance().getGameProfile().getName();
                default -> "%" + group + "%";
            };
        });
    }

    public void updateRichPresenceOnTitleScreen() {
        updateRichPresence(config.getTitleDisplay(), false);
    }

    public void updateRichPresenceOnPlayingSingleplayer() {
        assert displayConfig.playingSingleplayer() != null;
        updateRichPresence(displayConfig.playingSingleplayer(), false);
    }

    public void updateRichPresenceOnPlayingMultiplayer() {
        ServerData currentServer = Minecraft.getInstance().getCurrentServer();
        if(currentServer != null && currentServer.isRealm()) {
            assert displayConfig.playingRealms() != null;
            updateRichPresence(displayConfig.playingRealms(), false);
        } else {
            updateRichPresence(displayConfig.playingMultiplayer(), true);
        }
    }
}
