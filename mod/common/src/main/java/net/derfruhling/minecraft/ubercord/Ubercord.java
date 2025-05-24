package net.derfruhling.minecraft.ubercord;

import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.google.gson.Gson;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import net.derfruhling.minecraft.ubercord.client.Badge;
import net.derfruhling.minecraft.ubercord.client.ProvisionalServiceDownException;
import net.derfruhling.minecraft.ubercord.packets.*;
import net.derfruhling.minecraft.ubercord.server.AuthenticationConfig;
import net.derfruhling.minecraft.ubercord.server.AuthorizeUserRequest;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class Ubercord {
    public static final String MOD_ID = "ubercord";
    private static final Logger log = LogManager.getLogger(Ubercord.class);

    private static ServerConfig serverConfig;
    private static @Nullable AuthenticationConfig authConfig;

    static final JwkProvider jwks = new JwkProviderBuilder("https://maximum-honest-cat.ngrok-free.app/")
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build();

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

    static HttpClient homeHttp = createHomeHttp();

    private static HttpClient createHomeHttp() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return switch (getRequestingHost()) {
                            case "ubercord.derfruhling.net", "maximum-honest-cat.ngrok-free.app" ->
                                    new PasswordAuthentication(authConfig.clientId(), authConfig.clientKey().toCharArray());
                            default -> null;
                        };
                    }
                })
                .build();
    }

    static final HttpClient discordHttp = HttpClient.newBuilder()
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
            if(serverConfig != null && serverConfig.authKey() != null) {
                try {
                    authConfig = AuthenticationConfig.decode(serverConfig.authKey());
                } catch (ProvisionalServiceDownException e) {
                    log.warn("Provisional service is down", e);
                }
            }
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

                    discordHttp.sendAsync(HttpRequest.newBuilder()
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
                RequestJoinLobby.TYPE,
                RequestJoinLobby.STREAM_CODEC,
                (value, context) -> {
                    UUID serverId = serverConfig.serverId();
                    String secret = ChannelSecret.generateServerBasedSecret(serverId, value.lobbyName());
                    NetworkManager.sendToPlayer((ServerPlayer) context.getPlayer(), new JoinLobby(value.lobbyName(), secret));
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

        NetworkManager.registerReceiver(
                NetworkManager.c2s(),
                BeginProvisionalAuthorizationFlow.TYPE,
                BeginProvisionalAuthorizationFlow.STREAM_CODEC,
                (value, context) -> {
                    // TODO config
                    if(context.getPlayer().getGameProfile().getId().version() != 4) {
                        context.queue(() -> NetworkManager.sendToPlayer((ServerPlayer)context.getPlayer(), new ExchangeProvisionalSecret("OFFLINE")));
                    }

                    homeHttp.sendAsync(HttpRequest.newBuilder()
                                    .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(new AuthorizeUserRequest(
                                            context.getPlayer().getUUID(),
                                            value.state()
                                    ))))
                                    .uri(URI.create("https://maximum-honest-cat.ngrok-free.app/auth-server"))
                                    .build(), HttpResponse.BodyHandlers.ofString())
                            .handleAsync((response, throwable) -> {
                                if (throwable != null) {
                                    log.error("Failed to authorize user {}", context.getPlayer(), throwable);
                                    context.queue(() -> NetworkManager.sendToPlayer((ServerPlayer)context.getPlayer(), new ExchangeProvisionalSecret("")));
                                    homeHttp.close();
                                    homeHttp = createHomeHttp();
                                } else if(response.statusCode() != 200) {
                                    context.queue(() -> NetworkManager.sendToPlayer((ServerPlayer)context.getPlayer(), new ExchangeProvisionalSecret("")));
                                } else {
                                    String secret = response.body();
                                    context.queue(() -> NetworkManager.sendToPlayer((ServerPlayer)context.getPlayer(), new ExchangeProvisionalSecret(secret)));
                                }

                                return null;
                            })
                            .thenRun(homeHttp::close);
                }
        );
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
                ExchangeProvisionalSecret.TYPE,
                ExchangeProvisionalSecret.STREAM_CODEC
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
