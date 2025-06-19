package net.derfruhling.minecraft.ubercord;

import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import net.derfruhling.minecraft.ubercord.client.ProvisionalServiceDownException;
import net.derfruhling.minecraft.ubercord.packets.*;
import net.derfruhling.minecraft.ubercord.server.ServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class Ubercord {
    public static final String MOD_ID = "ubercord";
    private static final Logger log = LogManager.getLogger(Ubercord.class);

    private static ServerConfig serverConfig;

    private static @Nullable ManagedChannelService serverManagedChannelService = null;

    static final JwkProvider jwks;

    static {
        try {
            jwks = new JwkProviderBuilder(URI.create("https://ubercord.derfruhling.net/jwks.json").toURL())
                    .cached(10, 24, TimeUnit.HOURS)
                    .rateLimited(10, 1, TimeUnit.MINUTES)
                    .build();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    static final RSAKeyProvider key = new RSAKeyProvider() {
        @Override
        public RSAPublicKey getPublicKeyById(String s) {
            try {
                return (RSAPublicKey) jwks.get(s).getPublicKey();
            } catch (JwkException e) {
                throw new ProvisionalServiceDownException("Provisional service is down", e);
            }
        }

        @Override
        public RSAPrivateKey getPrivateKey() {
            return null;
        }

        @Override
        public String getPrivateKeyId() {
            return "";
        }
    };

    static final Algorithm rsa = Algorithm.RSA256(key);

    static final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public static byte[] decryptSecretText(byte[] text) {
        try {
            Cipher c = Cipher.getInstance("RSA");
            c.init(Cipher.DECRYPT_MODE, key.getPublicKeyById("enc"));
            return c.doFinal(text);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException |
                 BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    public static void init() {
        LifecycleEvent.SERVER_BEFORE_START.register(server -> {
            serverConfig = ServerConfig.loadOrDefault(server);
            serverManagedChannelService = new ManagedChannelService();
        });

        LifecycleEvent.SERVER_STOPPED.register(server -> {
            assert serverManagedChannelService != null;
            serverManagedChannelService.deleteAllTransientResources();
            serverManagedChannelService = null;
        });

        NetworkManager.registerReceiver(
                NetworkManager.c2s(),
                RequestGuildLink.TYPE,
                RequestGuildLink.STREAM_CODEC,
                (value, context) -> {
                    if(!context.getPlayer().hasPermissions(2)) return;

                    if(serverConfig.botToken() == null) {
                        context.getPlayer().sendSystemMessage(Component.translatable("ubercord.server.no_bot_token"));
                        return;
                    }

                    context.getPlayer().sendSystemMessage(Component.translatable("ubercord.server.link_start"));
                    long lobbyId = value.lobbyId();
                    long userId = ((DiscordIdContainer)context.getPlayer()).getUserId();

                    http.sendAsync(HttpRequest.newBuilder()
                            .PUT(HttpRequest.BodyPublishers.ofString("{\"flags\":1}"))
                            .uri(URI.create(String.format("https://discord.com/api/v10/lobbies/%d/members/%d", lobbyId, userId)))
                            .header("Authorization", "Bot " + serverConfig.botToken())
                            .header("User-Agent", "Ubercord (https://github.com/der-fruhling-entertainment/ubercord)")
                            .header("Content-Type", "application/json")
                            .build(), HttpResponse.BodyHandlers.ofString())
                            .handle((response, throwable) -> {
                                if(throwable != null) {
                                    context.getPlayer().sendSystemMessage(Component.translatable("ubercord.generic.error", throwable.toString()));
                                    log.error("Error setting lobby member permission flag", throwable);
                                } else if(response.statusCode() < 200 || response.statusCode() > 299) {
                                    context.getPlayer().sendSystemMessage(Component.translatable("ubercord.server.http_error", response.statusCode(), Objects.toString(response.body())));
                                    log.error("HTTP error setting lobby member permission flag: {} {}", response.statusCode(), response.body());
                                } else {
                                    NetworkManager.sendToPlayer((ServerPlayer) context.getPlayer(), new OpenGuildLink(lobbyId));
                                }

                                return null;
                            });
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.c2s(),
                RequestLobbyId.TYPE,
                RequestLobbyId.STREAM_CODEC,
                (value, context) -> {
                    assert serverManagedChannelService != null;
                    OwnedChannel channel = serverManagedChannelService.getOwnedChannel(value.lobbyName());

                    if(channel == null && serverConfig.permitsCreatingChannel(value.lobbyName())) {
                        try {
                            log.info("Creating server channel {}", value.lobbyName());
                            serverManagedChannelService.createServerChannel(value.lobbyName())
                                    .thenAccept(ownedChannel -> {
                                        log.info("Server channel {} has ID {}", value.lobbyName(), ownedChannel.lobbyId());
                                        NetworkManager.sendToPlayer((ServerPlayer) context.getPlayer(), new LobbyIdFound(ownedChannel.lobbyId(), value.lobbyName()));
                                    })
                                    .exceptionally(throwable -> {
                                        log.error("Failed to create channel {} in response to user trying to join it", value.lobbyName(), throwable);
                                        NetworkManager.sendToPlayer((ServerPlayer) context.getPlayer(), new LobbyIdFound(0, value.lobbyName()));
                                        return null;
                                    });
                        } catch (ChannelExistsException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        NetworkManager.sendToPlayer((ServerPlayer) context.getPlayer(), new LobbyIdFound(channel == null ? 0 : channel.lobbyId(), value.lobbyName()));
                    }
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.c2s(),
                JoinLobby.TYPE,
                JoinLobby.STREAM_CODEC,
                (value, context) -> {
                    assert serverManagedChannelService != null;

                    log.info("Player {} trying to join server lobby with ID {}", context.getPlayer(), value.lobbyId());

                    DiscordIdContainer container = (DiscordIdContainer) context.getPlayer();
                    OwnedChannel ownedChannel = serverManagedChannelService.getOwnedChannel(value.lobbyId());

                    if(ownedChannel == null) {
                        NetworkManager.sendToPlayer((ServerPlayer) context.getPlayer(), new LobbyJoinFailure(value.lobbyId(), "<not found; maybe try again?>"));
                        return;
                    }

                    if ((value.permissionToken().equals("$custom-in-use$") && serverManagedChannelService instanceof CustomManagedChannelService) || isPermissionJwtValid(value, container)) {
                        serverManagedChannelService.addUserToChannel(ownedChannel, container.getUserId(), value.permissionToken(), context.getPlayer().hasPermissions(3))
                                .exceptionally(throwable -> {
                                    log.error("Failed to authorize and add user {} to lobby {}", container.getUserId(), ownedChannel.lobbyId(), throwable);
                                    NetworkManager.sendToPlayer((ServerPlayer) context.getPlayer(), new LobbyJoinFailure(ownedChannel.lobbyId(), ownedChannel.name()));
                                    return null;
                                });
                    } else {
                        NetworkManager.sendToPlayer((ServerPlayer) context.getPlayer(), new LobbyJoinFailure(value.lobbyId(), ownedChannel.name()));
                    }
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.c2s(),
                LobbyLeftPacket.TYPE,
                LobbyLeftPacket.STREAM_CODEC,
                (value, context) -> {}
        );

        NetworkManager.registerReceiver(
                NetworkManager.c2s(),
                SetUserIdPacket.TYPE,
                SetUserIdPacket.STREAM_CODEC,
                (value, context) -> {
                    log.info("Player {} has Discord user ID {}", context.getPlayer(), value.userId());
                    ((DiscordIdContainer)context.getPlayer()).setUserId(value.userId());

                    NetworkManager.sendToPlayers(
                            Objects.requireNonNull(context.getPlayer().getServer()).getPlayerList().getPlayers(),
                            new NotifyAboutUserId(context.getPlayer().getName().getString(), context.getPlayer().getUUID(), value.userId(), value.isProvisional()));

                    NetworkManager.sendToPlayer((ServerPlayer) context.getPlayer(), new DeclareServerConfig(serverConfig));
                }
        );

        PlayerEvent.PLAYER_QUIT.register(player -> {
            if(((DiscordIdContainer)player).getUserId() > 0) {
                NetworkManager.sendToPlayers(
                        Objects.requireNonNull(player.getServer()).getPlayerList().getPlayers(),
                        new NotifyAboutUserId(player.getName().getString(), player.getUUID(), 0, false)
                );
            }
        });
    }

    private static boolean isPermissionJwtValid(JoinLobby value, DiscordIdContainer container) {
        try {
            JWT.require(rsa)
                    .withAudience("ubercord:join:" + container.getUserId())
                    .withSubject(String.valueOf(value.lobbyId()))
                    .withClaim("kind", "SERVER")
                    .build()
                    .verify(value.permissionToken());
            return true;
        } catch (JWTVerificationException e) {
            log.error("JWT failed verification", e);
            return false;
        }
    }

    public static void initDedicatedServer() {
        NetworkManager.registerS2CPayloadType(
                OpenGuildLink.TYPE,
                OpenGuildLink.STREAM_CODEC
        );

        NetworkManager.registerS2CPayloadType(
                JoinLobby.TYPE,
                JoinLobby.STREAM_CODEC
        );

        NetworkManager.registerS2CPayloadType(
                NotifyAboutUserId.TYPE,
                NotifyAboutUserId.STREAM_CODEC
        );

        NetworkManager.registerS2CPayloadType(
                DeclareServerConfig.TYPE,
                DeclareServerConfig.STREAM_CODEC
        );
    }
}
