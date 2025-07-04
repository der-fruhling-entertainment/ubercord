package net.derfruhling.minecraft.ubercord.client;


import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.mojang.authlib.GameProfile;
import dev.architectury.networking.NetworkManager;
import net.derfruhling.discord.socialsdk4j.*;
import net.derfruhling.minecraft.ubercord.*;
import net.derfruhling.minecraft.ubercord.gui.*;
import net.derfruhling.minecraft.ubercord.packets.*;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class SocialSdkIntegration {
    public static final long BUILTIN_CLIENT_ID = 1367390201621512313L;

    private static final Logger log = LogManager.getLogger(SocialSdkIntegration.class);
    private String accessToken = null;

    private record Cache(String accessToken, String refreshToken, boolean isProvisional) {}

    private final Client client = new Client();

    private @Nullable JoinedChannel currentChannel = null;
    private Map<String, JoinedChannel> knownChannels = new HashMap<>();
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
    private Avatar avatar = null;

    private final long start = Instant.now().getEpochSecond() * 1000;
    private final ManagedChannelService managedChannelService = new ManagedChannelService();

    public ClientConfig getConfig() {
        return config;
    }

    public Avatar getAvatar() {
        return avatar;
    }

    public void setConfig(ClientConfig config) {
        this.config = config;

        if(this.displayConfig == this.defaultConfig) {
            this.displayConfig = config.getDefaultConfig();
        }

        this.defaultConfig = config.getDefaultConfig();
    }

    public void setServerConfig(@Nullable DisplayConfig displayConfig) {
        if(displayConfig != null) {
            this.displayConfig = displayConfig;
        }
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

                    avatar = Avatar.forUser(Minecraft.getInstance(), self);

                    log.info("Executing {} deferred runnables", whenReady.size());
                    whenReady.forEach(Runnable::run);
                    whenReady.clear();
                } else if (isReady.get()) {
                    isReady.set(false);

                    if(chatFeaturesEnabled) {
                        generatePrebuiltMessage(Badge.YELLOW_EXCLAIM, Component.translatable("ubercord.auth.disconnected"));
                        generateChatAuthSelection(defaultClientId);
                        NetworkManager.sendToServer(SetUserIdPacket.DISCARD);
                    }

                    avatar = null;
                }
            }
        });

        client.setLobbyCreatedCallback(lobbyId -> {
            if(!chatFeaturesEnabled) {
                leaveLobby(lobbyId);
                return;
            }

            Lobby lobby = client.getLobby(lobbyId);

            if(lobby != null) {
                var meta = lobby.getMetadata();

                String name = meta.get("name");

                if(name != null) {
                    synchronized (this) {
                        JoinedChannel ch = new JoinedChannel(lobby);
                        knownChannels.put(name, ch);

                        if((currentChannel == null && userPrefs.preferredChannel == null) || (userPrefs.preferredChannel != null && userPrefs.preferredChannel.equals(name))) {
                            currentChannel = ch;

                            Minecraft.getInstance().execute(() -> {
                                generatePartyJoinMessage(name);
                                generateChannelSwitchMessage(name);
                            });
                        } else {
                            Minecraft.getInstance().execute(() -> {
                                generatePartyJoinMessage(name);
                            });
                        }
                    }

                    synchronized (channelTickers) {
                        StatusMessageRefTicker ticker = channelTickers.remove(name);
                        if(ticker != null) ticker.succeed();
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
                for (Map.Entry<String, JoinedChannel> lobby : knownChannels.entrySet().toArray(Map.Entry[]::new)) {
                    if(lobby.getValue().lobbyId() == lobbyId) {
                        if(channelTickers.containsKey(lobby.getValue().name())) {
                            channelTickers.remove(lobby.getValue().name()).succeed();
                        }

                        knownChannels.remove(lobby.getKey());

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

            Channel channel = msg.getChannel();

            if(msg.getDisclosureType() != null) {
                switch (msg.getDisclosureType()) {
                    case MessageDataVisibleOnDiscord -> {
                        if(self.isProvisional()) {
                            generatePrebuiltMessage(Badge.RED_EXCLAIM, Component.translatable("ubercord.disclosure.message_data.provisional").withColor(0xFFFFFFFF));
                        } else {
                            generatePrebuiltMessage(Badge.RED_EXCLAIM, Component.translatable("ubercord.disclosure.message_data").withColor(0xFFFFFFFF));
                        }
                    }
                }
            } else {
                if(channel != null && channel.type().isDm() && recipient != null) {
                    if(self.id == msg.getAuthorId()) {
//                        generateSentDmMessage(recipient, msg.getContent());
                    } else {
                        generateDmMessage(messageId, recipient, msg.getContent());
                    }
                } else {
                    Lobby lobby = msg.getLobby();
                    if(lobby == null) return;

                    if(author == null || author.id != self.id) {
                        receiveMessage(lobby.id, author, msg.getContent(), messageId);
                    }
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
                        generatePrebuiltMessage(Badge.STATUS_MESSAGE, Component.translatable("ubercord.invite.new", user.getDisplayName())
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
                        UbercordClient.show(r -> new SuddenInviteScreen(r, invite));
                    }
                }

                case JoinRequest -> {
                    if(Minecraft.getInstance().player != null) {
                        User user = client.getUser(invite.senderId());
                        assert user != null;
                        generatePrebuiltMessage(Badge.STATUS_MESSAGE, Component.translatable("ubercord.invite.join_request", user.getDisplayName())
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
                        UbercordClient.show(r -> new SuddenJoinRequestScreen(r, invite));
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

    private void leaveLobby(long lobbyId) {
        Lobby lobby = client.getLobby(lobbyId);
        if(lobby == null) return;

        Map<String, String> meta = lobby.getMetadata();
        String name = meta.get("name");

        if(meta.containsKey("server-managed") && meta.get("server-managed").equals("true")) {
            MutableComponent activity = Component.translatable("ubercord.lobby.leaving", name)
                    .withStyle(ChatFormatting.GRAY);
            MutableComponent status = Component.translatable("ubercord.lobby.status.waiting_for_server")
                    .withStyle(ChatFormatting.DARK_GRAY);
            StatusMessageRefTicker lobbyStatus = StatusMessageRefTicker.create(activity, status);
            channelTickers.put(name, lobbyStatus);

            // can't leave here, need to interact with the server
            assert accessToken != null;
            NetworkManager.sendToServer(new LeaveLobby(lobbyId));
        } else if(meta.containsKey("ups-managed") && meta.get("ups-managed").equals("true")) {
            MutableComponent activity = Component.translatable("ubercord.lobby.leaving", name)
                    .withStyle(ChatFormatting.GRAY);
            MutableComponent status = Component.translatable("ubercord.lobby.status.waiting_for_server")
                    .withStyle(ChatFormatting.DARK_GRAY);
            StatusMessageRefTicker lobbyStatus = StatusMessageRefTicker.create(activity, status);
            channelTickers.put(name, lobbyStatus);

            // can't leave here, need to interact with provisional service
            assert accessToken != null;
            managedChannelService.removeSelfFromChannel(lobbyId, accessToken)
                    .exceptionally(throwable -> {
                        lobbyStatus.fail();
                        log.error("Failed to remove self from lobby {}", name, throwable);
                        return null;
                    });
        } else {
            MutableComponent activity = Component.translatable("ubercord.lobby.leaving", name)
                    .withStyle(ChatFormatting.GRAY);
            MutableComponent status = Component.empty();
            StatusMessageRefTicker lobbyStatus = StatusMessageRefTicker.create(activity, status);
            channelTickers.put(name, lobbyStatus);

            client.leaveLobby(lobbyId, result -> {
                if(!result.isSuccess()) {
                    lobbyStatus.fail();
                    log.error("Failed to leave lobby {}: {}", lobbyId, result.message());
                } else {
                    knownChannels.remove(name);

                    if(currentChannel != null && currentChannel.lobbyId() == lobby.id) {
                        currentChannel = knownChannels.values().stream().findFirst().orElse(null);
                    }
                }
            });
        }
    }

    public void leaveLobby(String name) {
        leaveLobby(Objects.requireNonNull(getJoinedChannel(name)).lobbyId());
    }

    private final HashMap<String, StatusMessageRefTicker> channelTickers = new HashMap<>();

    public void joinLobby(ManagedChannelKind ctx, String name) {
        switch (ctx) {
            case GLOBAL -> {
                MutableComponent activity = Component.translatable("ubercord.lobby.joining", name)
                        .withStyle(ChatFormatting.GRAY);
                MutableComponent status = Component.empty();
                StatusMessageRefTicker lobbyStatus = StatusMessageRefTicker.create(activity, status);
                channelTickers.put(name, lobbyStatus);

                joinGlobalLobby(name);
            }

            case SERVER -> {
                if(NetworkManager.canServerReceive(RequestLobbyId.TYPE)) {
                    synchronized (channelTickers) {
                        if(channelTickers.containsKey(name)) return;

                        MutableComponent activity = Component.translatable("ubercord.lobby.joining", name)
                                .withStyle(ChatFormatting.GRAY);
                        MutableComponent status = Component.translatable("ubercord.lobby.status.resolving")
                                .withStyle(ChatFormatting.DARK_GRAY);
                        StatusMessageRefTicker lobbyStatus = StatusMessageRefTicker.create(activity, status);
                        channelTickers.put(name, lobbyStatus);
                    }

                    NetworkManager.sendToServer(new RequestLobbyId(name));
                } else {
                    generatePrebuiltMessage(Badge.YELLOW_EXCLAIM, Component.translatable("ubercord.generic.joining_global_warning"));
                    joinLobby(ManagedChannelKind.GLOBAL, name);
                }
            }

            case PERSONAL -> throw new NotImplementedException(); // TODO
        }
    }

    public void tick() {
        channelTickers.forEach((name, ticker) -> {
            ticker.tick();
        });
    }

    /**
     *
     * @param name Name of the channel
     * @param secret Unique secret for this channel
     * @deprecated Do not use this
     *
     * @see SocialSdkIntegration#joinGlobalLobby
     */
    @Deprecated(forRemoval = true)
    public void joinLobby(String name, String secret) {
        Player player = Minecraft.getInstance().player;
        if(player == null) return;

        Map<String, String> lobbyMeta = new HashMap<>(), memberMeta = new HashMap<>();
        lobbyMeta.put("kind", "GLOBAL");
        lobbyMeta.put("channel-name", name);
        lobbyMeta.put("secret", secret);

        client.createOrJoinLobby(secret, lobbyMeta, memberMeta, (result, lobbyId) -> {
            if(!result.isSuccess()) {
                generatePrebuiltMessage(Badge.STATUS_MESSAGE, Component.translatable("ubercord.generic.error", result.message()));
            }
        });
    }

    void lobbyJoinFailed(String name) {
        generatePrebuiltMessage(Badge.RED_EXCLAIM, Component.translatable("ubercord.lobby.failure"));

        synchronized (channelTickers) {
            StatusMessageRefTicker ticker = channelTickers.remove(name);
            if(ticker != null) ticker.fail();
        }
    }

    void joinServerLobby(long lobbyId, String name, boolean usingCustomService) {
        if(lobbyId == 0) {
            generatePrebuiltMessage(Badge.RED_EXCLAIM, Component.translatable("ubercord.lobby.not_found_error"));

            synchronized (channelTickers) {
                StatusMessageRefTicker ticker = channelTickers.remove(name);
                if(ticker != null) ticker.fail();
            }

            return;
        }

        if(!usingCustomService) {
            managedChannelService.requestPermissionsToken(lobbyId, ManagedChannelKind.SERVER, accessToken)
                    .thenAccept(s -> {
                        NetworkManager.sendToServer(new JoinLobby(lobbyId, s));
                        channelTickers.get(name).updateStatus(Component.translatable("ubercord.lobby.status.waiting_for_server").withStyle(ChatFormatting.DARK_GRAY));
                    })
                    .exceptionally(e -> {
                        log.error("Failed to join server lobby {} named {}", lobbyId, name, e);
                        generatePrebuiltMessage(Badge.RED_EXCLAIM, Component.translatable("ubercord.lobby.provisional_service_failed").withStyle(ChatFormatting.RED));

                        synchronized (channelTickers) {
                            StatusMessageRefTicker ticker = channelTickers.remove(name);
                            if(ticker != null) ticker.fail();
                        }

                        return null;
                    });

            channelTickers.get(name).updateStatus(Component.translatable("ubercord.lobby.status.authorizing").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            NetworkManager.sendToServer(new JoinLobby(lobbyId, "$custom-in-use$"));
            channelTickers.get(name).updateStatus(Component.translatable("ubercord.lobby.status.waiting_for_server").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public void joinGlobalLobby(String name) {
        Player player = Minecraft.getInstance().player;
        if(player == null) return;

        String secret = ChannelSecret.generateGlobalSecret(name);

        Map<String, String> lobbyMeta = new HashMap<>(), memberMeta = new HashMap<>();
        lobbyMeta.put("kind", "GLOBAL");
        lobbyMeta.put("name", name);
        lobbyMeta.put("secret", secret);

        client.createOrJoinLobby(secret, lobbyMeta, memberMeta, (result, lobbyId) -> {
            if(!result.isSuccess()) {
                generatePrebuiltMessage(Badge.STATUS_MESSAGE, Component.translatable("ubercord.generic.error", result.message()));

                lobbyJoinFailed(name);
            }
        });
    }

    public synchronized void leaveServer() {
        knownChannels.forEach((s, joinedChannel) -> {
            if(joinedChannel.kind() == ManagedChannelKind.SERVER) {
                leaveLobby(joinedChannel.lobbyId());
            }
        });
        currentChannel = null;
        displayConfig = defaultConfig;
        channelTickers.clear();
    }

    public static void connectWithJoinSecret(String joinSecret) {
        JoinSecret secret = JoinSecret.decode(joinSecret);
        log.info("Joining {}'s game: {}:{}", secret.inviter(), secret.ip(), secret.port());

        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if(conn != null && !Minecraft.getInstance().isSingleplayer()) {
            conn.handleTransfer(new ClientboundTransferPacket(secret.ip(), secret.port()));
        } else {
            if(conn != null && Minecraft.getInstance().isSingleplayer()) {
                Minecraft.getInstance().disconnect(new ProgressScreen(true), false);
            }

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
        leaveServer();

        client.disconnect();
    }

    public Set<String> getOnlinePlayers() {
        return onlinePlayers.keySet();
    }

    public void updatePlayer(String username, UUID uuid, long userId, boolean isProvisional) {
        Path playerInfoPath = Path.of("ubercord", "player_list", userId + ".json");

        try {
            Files.createDirectories(playerInfoPath.getParent());
            Files.writeString(playerInfoPath, new Gson().toJson(new RemotePlayerInfo(username, uuid)));

            Objects.requireNonNull(UbercordClient.getFriendsListScreen()).ensureAvatarsAreReloaded(userId, isProvisional);
        } catch (IOException e) {
            log.error("Failed to save player info for {} (player {} \"{}\")", userId, uuid, username, e);
        }

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
            updatePlayer(packet.username(), packet.uuid(), packet.userId(), packet.isProvisional());
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

    public Iterable<Map.Entry<String, JoinedChannel>> getJoinedChannels() {
        return knownChannels.entrySet();
    }

    public @Nullable JoinedChannel getJoinedChannel(String name) {
        return knownChannels.get(name);
    }

    public @Nullable JoinedChannel getCurrentChannel() {
        return currentChannel;
    }

    private void setChannel(@NotNull JoinedChannel ch) {
        currentChannel = ch;

        Minecraft.getInstance().execute(() -> {
            Map<String, String> meta = ch.lobby().getMetadata();
            generateChannelSwitchMessage(meta.get("name"));

            if(Minecraft.getInstance().screen instanceof DiesOnChannelChange) {
                Minecraft.getInstance().setScreen(null);
            }
        });
    }

    public void setChannel(String channel) {
        JoinedChannel ch = getJoinedChannel(channel);

        if(ch == null) {
            joinLobby(ManagedChannelKind.SERVER, channel);
        } else {
            setChannel(ch);
        }

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

        knownChannels.clear();

        File cacheDir = new File(Minecraft.getInstance().gameDirectory, "ubercord");
        File cacheFile = new File(cacheDir, "social-sdk-" + clientId + ".json");

        if(chatFeaturesEnabled) {
            if(cacheFile.exists()) {
                try {
                    String text = Files.readString(cacheFile.toPath());
                    Cache cache = new Gson().fromJson(text, Cache.class);

                    if(!cache.isProvisional) {
                        UbercordClient.getStatus().reset(Component.translatable("ubercord.auth.discord.status"), Component.empty());
                        onTokenGet(clientId, cache.accessToken, cache.refreshToken, false);
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

                        this.accessToken = cache.accessToken;
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

    private void generateChatAuthSelection(long clientId) {
        if(Minecraft.getInstance().screen == null) {
            Minecraft.getInstance().gui.getChat().addMessage(Component
                    .translatable("ubercord.auth.selector.title")
                    .append(Component.translatable("ubercord.auth.selector.discord")
                            .withStyle(Style.EMPTY
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl authorize discord " + clientId))
                                    .withColor(0x5865F2)
                                    .withUnderlined(true)))
                    .append("\n")
                    .append(Component.translatable("ubercord.auth.selector.provisional")
                            .withStyle(Style.EMPTY
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl authorize try-provisional " + clientId))
                                    .withColor(ChatFormatting.GRAY)
                                    .withUnderlined(true)))
            );
        } else {
            UbercordClient.show(runnable -> new AuthTypeSelectorScreen(runnable) {
                @Override
                protected Screen startDiscordFlow(Runnable returnToParent) {
                    authorizeReal(clientId, clientId != BUILTIN_CLIENT_ID || ENABLE_CHAT_FEATURES_ON_DEFAULT_CLIENT);
                    return new StatusAwaiterScreen(returnToParent);
                }

                @Override
                protected Screen startProvisionalFlow(Runnable returnToParent) {
                    authorizeProvisional(clientId);
                    return new StatusAwaiterScreen(returnToParent);
                }
            });
        }
    }

    // TODO use provisional merge here as the docs say (keeps it unique with provisional accts)
    void authorizeReal(long clientId, boolean chatFeaturesEnabled) {
        String state = Minecraft.getInstance().getUser().getName() + ":" + new Random().nextLong();
        CodeVerifier ver = client.createAuthorizationCodeVerifier();

        generatePrebuiltMessage(Badge.STATUS_MESSAGE, Component.translatable("ubercord.auth.discord.begin"));
        UbercordClient.getStatus().reset(Component.translatable("ubercord.auth.discord.status"), Component.translatable("ubercord.auth.discord.authorizing"));

        client.authorize(clientId, chatFeaturesEnabled ? getCommunicationsScopes() : Client.PRESENCE_SCOPES, state, ver.challenge(), (result, code, redirectUri) -> {
            if (result.isSuccess()) {
                UbercordClient.getStatus().setStatusInfo(Component.translatable("ubercord.auth.discord.poking_discord"));
                client.getToken(clientId, code, ver.verifier(), redirectUri, (result1, accessToken, refreshToken, type, expiresIn, scopes) -> {
                    if (result1.isSuccess()) {
                        onTokenGet(clientId, accessToken, refreshToken, false);
                    } else {
                        log.error("Failed to poke Discord: {}", result1.message());
                        generatePrebuiltMessage(Badge.RED_EXCLAIM, Component.translatable("ubercord.auth.discord.failed_token", result1.message()));
                        UbercordClient.getStatus().fail();
                        authorizeProvisional(clientId);
                    }
                });
            } else {
                log.error("Failed to authorize with Discord: {}", result.message());
                generatePrebuiltMessage(Badge.RED_EXCLAIM, Component.translatable("ubercord.auth.discord.failed_auth", result.message()));
                UbercordClient.getStatus().fail();
                authorizeProvisional(clientId);
            }
        });
    }

    private static String[] getCommunicationsScopes() {
        String[] scopes = new String[Client.COMMUNICATIONS_SCOPES.length + 2];
        scopes[0] = "bot";
        scopes[1] = "application.commands";
        System.arraycopy(Client.COMMUNICATIONS_SCOPES, 0, scopes, 2, Client.COMMUNICATIONS_SCOPES.length);
        return scopes;
    }

    private void onTokenGet(long clientId, String accessToken, String refreshToken, boolean isProvisional) {
        generatePrebuiltMessage(Badge.STATUS_MESSAGE, Component.translatable("ubercord.auth.authorization_successful"));
        UbercordClient.getStatus().setStatusInfo(Component.translatable("ubercord.auth.common.authorized"));
        Cache cache = new Cache(accessToken, refreshToken, isProvisional);

        File cacheDir = new File(Minecraft.getInstance().gameDirectory, "ubercord");
        if(!cacheDir.exists()) cacheDir.mkdirs();
        File cacheFile = new File(cacheDir, "social-sdk-" + clientId + ".json");

        try {
            Files.writeString(cacheFile.toPath(), new Gson().toJson(cache), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        generatePrebuiltMessage(Badge.STATUS_MESSAGE, Component.translatable("ubercord.auth.connecting"));
        client.updateToken(AuthorizationTokenType.Bearer, cache.accessToken, result2 -> {
            if (result2.isSuccess()) {
                client.connect();
                UbercordClient.getStatus().reset(Component.translatable("ubercord.auth.common.done"), Component.empty());
                UbercordClient.getStatus().succeed();
            } else {
                generatePrebuiltMessage(Badge.RED_EXCLAIM, Component.translatable("ubercord.auth.update_token_error", result2.message()));
                if(!isProvisional) authorizeProvisional(clientId);
            }
        });

        this.accessToken = accessToken;
    }

    private final AtomicBoolean isWaitingForProvisional = new AtomicBoolean(false);
    private final AtomicReference<String> provisionalState = new AtomicReference<>();
    private long clientId = 0;

    boolean hasAgreedToProvisionalDisclaimer() {
        return config.hasAgreedToProvisionalServiceUsage();
    }

    void printProvisionalDisclaimer() {
        generatePrebuiltMessage(Badge.STATUS_MESSAGE, Component.translatable("ubercord.auth.provisional.disclaimer"));

        // The project really should provide a privacy policy to dictate
        // exactly what is used and how. Hopefully this effort will be enough.
        String[] supportedLanguages = {"en_us"};
        String currentLanguage = Minecraft.getInstance().getLanguageManager().getSelected();
        String privacyUrl;

        if(Arrays.asList(supportedLanguages).contains(currentLanguage)) {
            privacyUrl = "https://derfrühling.net/ubercord/privacy/" + currentLanguage;
        } else {
            privacyUrl = "https://derfrühling.net/ubercord/privacy/en_us";
        }

        Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("ubercord.auth.provisional.disclaimer.privacy")
                .append(Component.literal(privacyUrl).withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, privacyUrl)).withUnderlined(true).withColor(ChatFormatting.BLUE)))
                .append("\n\n")
                .append(Component.translatable("ubercord.auth.provisional.disclaimer.accept")
                        .withStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl authorize provisional " + clientId))
                                .withColor(ChatFormatting.GRAY)
                                .withUnderlined(true)))
                .append("\n\n")
                .append(Component.translatable("ubercord.auth.provisional.disclaimer.decline")
                        .withStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl authorize discord " + clientId))
                                .withColor(0x5865F2)
                                .withUnderlined(true)))
                .append("\n")
        );
    }

    void authorizeProvisional(long clientId) {
        config.setHasAgreedToProvisionalServiceUsage(true);
        try {
            config.save();
        } catch (IOException e) {
            log.error("Failed to record user consent to provisional service usage in config, you will be asked again next time!", e);
        }

        generatePrebuiltMessage(Badge.STATUS_MESSAGE, Component.translatable("ubercord.auth.provisional.begin"));
        UbercordClient.getStatus().reset(Component.translatable("ubercord.auth.provisional.status"), Component.translatable("ubercord.auth.provisional.authorizing"));
        this.clientId = clientId;

        GameProfile player = Minecraft.getInstance().getGameProfile();
        try {
            Minecraft m = Minecraft.getInstance();
            ProfileKeyPairManager mgr = m.getProfileKeyPairManager();

            if(!(mgr instanceof AccountProfileKeyPairManager)) {
                throw new OfflineException("Offline mode is not supported");
            }

            mgr.prepareKeyPair().handleAsync((profileKeyPair, throwable) -> {
                try {
                    if(profileKeyPair.isEmpty()) {
                        throw new OfflineException("Offline mode is not supported");
                    }

                    PrivateKey privateKey = profileKeyPair.get().privateKey();
                    ProfilePublicKey profilePublicKey = profileKeyPair.get().publicKey();

                    String bodyText = new Gson().toJson(new ProvisionalRequest(
                            player.getName(),
                            player.getId(),
                            profilePublicKey.data().expiresAt().toEpochMilli()
                    ));

                    Signature signature = Signature.getInstance("SHA256withRSA");
                    signature.initSign(privateKey);
                    signature.update(bodyText.getBytes(StandardCharsets.UTF_8));
                    String signatureText = Base64.getUrlEncoder().encodeToString(signature.sign());
                    String publicKeyText = Base64.getUrlEncoder().encodeToString(profilePublicKey.data().key().getEncoded());
                    String publicKeySignatureText = Base64.getUrlEncoder().encodeToString(profilePublicKey.data().keySignature());

                    log.info("Interacting with the provisional service");
                    http.sendAsync(HttpRequest.newBuilder()
                                    .POST(HttpRequest.BodyPublishers.ofString(
                                            bodyText
                                    ))
                                    .uri(URI.create("https://ubercord.derfruhling.net/authorize"))
                                    .header("X-Signature", signatureText)
                                    .header("X-PublicKey", publicKeyText)
                                    .header("X-PublicKeySignature", publicKeySignatureText)
                                    .header("Content-Type", "application/json")
                                    .header("Accept", "text/plain")
                                    .build(), HttpResponse.BodyHandlers.ofString())
                            .handleAsync((response, throwable1) -> {
                                if(throwable1 != null) {
                                    generatePrebuiltMessage(Badge.STATUS_MESSAGE, Component.translatable("ubercord.auth.provisional.failed_throwable", throwable1.getMessage()));
                                    UbercordClient.getStatus().fail();
                                } else if(response.statusCode() != 200) {
                                    generatePrebuiltMessage(Badge.STATUS_MESSAGE, Component.translatable("ubercord.auth.provisional.failed_http", response.statusCode()));
                                    UbercordClient.getStatus().fail();
                                } else {
                                    generatePrebuiltMessage(Badge.STATUS_MESSAGE, Component.translatable("ubercord.auth.provisional.got_token"));
                                    UbercordClient.getStatus().setStatusInfo(Component.translatable("ubercord.auth.provisional.poking_discord"));
                                    client.getProvisionalToken(clientId, ExternalAuthType.OpenIDConnect, response.body(), (result, accessToken, refreshToken, type, expiresIn, scopes) -> {
                                        if (result.isSuccess()) {
                                            onTokenGet(clientId, accessToken, refreshToken, true);
                                        } else {
                                            generatePrebuiltMessage(Badge.STATUS_MESSAGE, Component.translatable("ubercord.auth.provisional.failed_token", result.message()));
                                            UbercordClient.getStatus().fail();
                                            generateChatAuthSelection(clientId);
                                        }
                                    });
                                }

                                return null;
                            });
                } catch (OfflineException e) {
                    generatePrebuiltMessage(Badge.RED_EXCLAIM, Component.translatable("ubercord.auth.provisional.offline_mode_unsupported"));
                } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
                    log.error("Encountered error while attempting provisional flow", e);
                    throw new RuntimeException(e);
                }

                return null;
            }).exceptionally(throwable -> {
                log.error("An error occurred processing the provisional request", throwable);
                UbercordClient.getStatus().fail();
                return null;
            });
        } catch (OfflineException e) {
            generatePrebuiltMessage(Badge.RED_EXCLAIM, Component.translatable("ubercord.auth.provisional.offline_mode_unsupported"));
        }
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

    public Client getClient() {
        return client;
    }

    public void sendMessage(String message) {
        if(Minecraft.getInstance().player == null) return;

        if(currentChannel == null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.translatable("ubercord.generic.no_channel_selected_error"));
        } else if(isReady.get()) {
            Map<String, String> meta = currentChannel.lobby().getMetadata();

            if(meta.containsKey("server-default") && meta.get("server-default").equals("true")) {
                ((CanSendSignedMessage) Objects.requireNonNull(Minecraft.getInstance().getConnection())).sendSignedMessageToServer(message);
            }

            reallySendMessage(currentChannel.lobbyId(), message);
        }
    }

    private void sendMessage(long lobbyId, String message) {
        if(Minecraft.getInstance().player == null) return;

        if(isReady.get()) {
            reallySendMessage(lobbyId, message);
        }
    }

    public void sendMessage(String channel, String message) {
        JoinedChannel ch = getJoinedChannel(channel);

        if(ch == null) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.translatable("ubercord.generic.unknown_channel_error", channel).withStyle(ChatFormatting.RED));
            }

            return;
        }

        if(isReady()) {
            sendMessage(ch.lobbyId(), message);
        } else {
            whenReady.add(() -> sendMessage(ch.lobbyId(), message));
        }
    }

    private void reallySendMessage(long lobbyId, String message) {
        StatusChanger changer;
        if(currentChannel != null && currentChannel.lobbyId() == lobbyId) {
            changer = generateMainLobbyMessage(self, message);
        } else {
            Lobby lobby = client.getLobby(lobbyId);
            if(lobby != null) {
                Map<String, String> meta = lobby.getMetadata();
                changer = generateLobbyMessage(meta.get("channel-name"), self, message);
            } else {
                changer = null;
            }
        }

        client.sendLobbyMessage(lobbyId, message, (result, messageId) -> {
            if(Minecraft.getInstance().player == null) return;

            if(changer != null) {
                changer.invoke(result.isSuccess(), messageId);
            }

            if (!result.isSuccess()) {
                Minecraft.getInstance().player.sendSystemMessage(Component.translatable("ubercord.generic.error", result.message()));
            }
        });
    }

    private void receiveMessage(long lobbyId, User source, String message, long messageId) {
        if(currentChannel != null && currentChannel.lobbyId() == lobbyId) {
            generateMainLobbyMessage(source, message)
                    .invoke(true, messageId);
        } else {
            Lobby lobby = client.getLobby(lobbyId);
            if(lobby != null) {
                Map<String, String> meta = lobby.getMetadata();
                generateLobbyMessage(meta.get("channel-name"), source, message)
                        .invoke(true, messageId);
            }
        }
    }

    enum MessageStatus {
        SENT {
            @Override
            Style style() {
                return Style.EMPTY
                        .withColor(ChatFormatting.GRAY);
            }
        },
        CONFIRMED {
            @Override
            Style style() {
                return Style.EMPTY
                        .withColor(ChatFormatting.WHITE);
            }
        },
        BROKEN {
            @Override
            Style style() {
                return Style.EMPTY
                        .withColor(ChatFormatting.RED);
            }
        };

        abstract Style style();
    }

    public void generateDmMessage(
            long messageId,
            User source,
            String message
    ) {
        Minecraft client = Minecraft.getInstance();
        if(!(client.screen instanceof HandlesNewMessage)) {
            OnlinePlayer player = onlinePlayersById.get(source.id);

            MutableComponent msg = Component.literal(message).withStyle(ChatFormatting.WHITE);
            MutableComponent root = Component.empty()
                    .withStyle(ChatFormatting.GRAY)
                    .append("[")
                    .append(player != null
                            ? Badge.ONLINE_USER_MESSAGE.create(player.username, source.getDisplayName(), source.id, source.getRelationship())
                            : Badge.OFFLINE_USER_MESSAGE.create(null, source.getDisplayName(), source.id, source.getRelationship()))
                    .append(Badge.componentForUser(source, player != null ? player.username : null))
                    .append(" → ")
                    .append(Component.translatable("ubercord.chat.you"))
                    .append("] ")
                    .append(msg)
                    .append("  ")
                    .append(Badge.REPLY_BUTTON.create()
                            .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/dm " + source.id + " "))));

            if(messageId != 0) {
                refs.put(messageId, new MessageRef(root, msg, MessageStatus.CONFIRMED));
            }

            client.gui.getChat().addMessage(
                    root,
                    null,
                    null
            );

            FriendListScreen friendsListScreen = UbercordClient.getFriendsListScreen();
            if(friendsListScreen != null) {
                Message m = getClient().getMessage(messageId);
                friendsListScreen.onNewUserMessage(source, m);
            }
        } else {
            Message m = getClient().getMessage(messageId);
            ((HandlesNewMessage) client.screen).onNewUserMessage(source, m);
        }
    }

    public interface StatusChanger {
        void invoke(boolean successful, long messageId);

        StatusChanger NO_OP = (successful, messageId) -> {};
    }

    public StatusChanger generateSentDmMessage(
            User target,
            String message
    ) {
        Minecraft client = Minecraft.getInstance();
        OnlinePlayer player = onlinePlayersById.get(target.id);

        MutableComponent msg = Component.literal(message).withStyle(ChatFormatting.GRAY);
        MutableComponent root = Component.empty()
                .withStyle(ChatFormatting.GRAY)
                .append("[")
                .append(Component.translatable("ubercord.chat.you"))
                .append(" → ")
                .append(player != null
                        ? Badge.ONLINE_USER_MESSAGE.create(player.username, target.getDisplayName(), target.id, target.getRelationship())
                        : Badge.OFFLINE_USER_MESSAGE.create(null, target.getDisplayName(), target.id, target.getRelationship()))
                .append(Badge.componentForUser(target, player != null ? player.username : null))
                .append("] ")
                .append(msg);

        MessageRef ref = new MessageRef(root, msg, MessageStatus.SENT);
        StatusChanger changer = (successful, messageId) -> {
            if(successful) {
                ref.changeStatus(MessageStatus.CONFIRMED);

                FriendListScreen friendsListScreen = UbercordClient.getFriendsListScreen();
                if(friendsListScreen != null && !(client.screen instanceof HandlesNewMessage)) {
                    Message m = getClient().getMessage(messageId);
                    friendsListScreen.onNewSelfMessageUnfocused(target, m);
                }
            } else {
                ref.changeStatus(MessageStatus.BROKEN);
            }
        };

        client.gui.getChat().addMessage(
                root,
                null,
                null
        );

        return changer;
    }

    public StatusChanger generateLobbyMessage(
            String lobbyName,
            User source,
            String message
    ) {
        OnlinePlayer player = onlinePlayersById.get(source.id);

        MutableComponent msg = Component.literal(message).withStyle(source.id == self.id ? ChatFormatting.GRAY : ChatFormatting.WHITE);
        MutableComponent root = Component.empty()
                .withStyle(ChatFormatting.GRAY)
                .append("~")
                .append(Component.literal(lobbyName)
                        .withStyle(Style.EMPTY
                                .withColor(ChatFormatting.BLUE)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "~" + lobbyName + " "))))
                .append(" [")
                .append(player != null
                        ? Badge.ONLINE_USER_MESSAGE.create(player.username, source.getDisplayName(), source.id, source.getRelationship())
                        : Badge.OFFLINE_USER_MESSAGE.create(null, source.getDisplayName(), source.id, source.getRelationship()))
                .append(Badge.componentForUser(source, player != null ? player.username : null))
                .append("] ")
                .append(msg);

        MessageRef ref = new MessageRef(root, msg, source.id == self.id ? MessageStatus.SENT : MessageStatus.CONFIRMED);
        StatusChanger changer = (successful, messageId) -> {
            if(source.id == self.id) {
                ref.changeStatus(successful ? MessageStatus.CONFIRMED : MessageStatus.BROKEN);
            }

            if(successful) {
                refs.put(messageId, ref);
            }
        };

        Minecraft.getInstance().gui.getChat().addMessage(
                root,
                null,
                null
        );

        return changer;
    }

    public StatusChanger generateMainLobbyMessage(
            User source,
            String message
    ) {
        OnlinePlayer player = onlinePlayersById.get(source.id);

        MutableComponent msg = Component.literal(message).withStyle(source.id == self.id ? ChatFormatting.GRAY : ChatFormatting.WHITE);
        MutableComponent root = Component.empty()
                .withStyle(ChatFormatting.GRAY)
                .append("[")
                .append(player != null
                        ? Badge.ONLINE_USER_MESSAGE.create(player.username, source.getDisplayName(), source.id, source.getRelationship())
                        : Badge.OFFLINE_USER_MESSAGE.create(null, source.getDisplayName(), source.id, source.getRelationship()))
                .append(Badge.componentForUser(source, player != null ? player.username : null))
                .append("] ")
                .append(msg);

        MessageRef ref = new MessageRef(root, msg, source.id == self.id ? MessageStatus.SENT : MessageStatus.CONFIRMED);
        StatusChanger changer = (successful, messageId) -> {
            if(source.id == self.id) {
                ref.changeStatus(successful ? MessageStatus.CONFIRMED : MessageStatus.BROKEN);
            }

            if(successful) {
                refs.put(messageId, ref);
            }
        };

        Minecraft.getInstance().gui.getChat().addMessage(
                root,
                null,
                null
        );

        return changer;
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
            refs.put(messageId, new MessageRef(root, msg, MessageStatus.CONFIRMED));
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
        if(Minecraft.getInstance().screen != null) return;

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
        generatePrebuiltMessage(Badge.STATUS_MESSAGE, Component.translatable("ubercord.party.join", partyName));
    }

    public void generatePartyLeaveMessage(
            String partyName
    ) {
        generatePrebuiltMessage(Badge.STATUS_MESSAGE, Component.translatable("ubercord.party.leave", partyName));
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
        ActivityBuilder activity = new ActivityBuilder()
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

            activity.setAssets(new ActivityBuilder.Assets(
                    largeImage != null ? new ActivityBuilder.Asset(largeImage, largeText) : null,
                    smallImage != null ? new ActivityBuilder.Asset(smallImage, smallText) : null
            ));
        }

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
            final Minecraft client = Minecraft.getInstance();
            return switch(group) {
                case "dimension_id" -> client.player != null
                        ? client.player.level().dimension().location().toString()
                        : "%" + group + "%";
                case "dimension" -> client.player != null
                        ? displayConfig.dimensionNames().getOrDefault(client.player.level().dimension().location().toString(), "%" + group + "%")
                        : "%" + group + "%";
                case "world_name" -> client.getSingleplayerServer() != null
                        ? client.getSingleplayerServer().getWorldData().getLevelName()
                        : "%" + group + "%";
                case "player_name" -> client.getGameProfile().getName();
                case "version" -> SharedConstants.getCurrentVersion().getName();
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
