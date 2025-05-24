package net.derfruhling.minecraft.ubercord;

import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.google.gson.Gson;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import net.derfruhling.minecraft.ubercord.packets.*;
import net.derfruhling.minecraft.ubercord.server.AuthenticationConfig;
import net.derfruhling.minecraft.ubercord.server.AuthorizeUserRequest;
import net.derfruhling.minecraft.ubercord.server.ServerConfig;
import net.minecraft.commands.Commands;
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

    private static @Nullable ServerConfig serverConfig;
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
                throw new RuntimeException(e);
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
            .authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return switch(getRequestingHost()) {
                        case "ubercord.derfruhling.net", "maximum-honest-cat.ngrok-free.app" -> new PasswordAuthentication(authConfig.clientId(), authConfig.clientKey().toCharArray());
                        default -> null;
                    };
                }
            })
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
        LifecycleEvent.SERVER_LEVEL_LOAD.register(level -> {
            serverConfig = ServerConfig.loadMaybe();
            if(serverConfig != null && serverConfig.authKey() != null) {
                authConfig = AuthenticationConfig.decode(serverConfig.authKey());
            }
        });

        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            dispatcher.register(Commands.literal("link")
                    .requires(stack -> {
                        if (!stack.hasPermission(2)) return false;

                        assert serverConfig != null;
                        return serverConfig.botToken() != null;
                    })
                    .executes(commandContext -> {
                        ServerPlayer player = commandContext.getSource().getPlayer();

                        if(player != null) {
                            NetworkManager.sendToPlayer(player, OpenUbercordGuildLinkScreenPacket.INSTANCE);
                            return 0;
                        } else {
                            return 1;
                        }
                    }));
        });

        NetworkManager.registerReceiver(
                NetworkManager.c2s(),
                RequestJoinLobby.TYPE,
                RequestJoinLobby.STREAM_CODEC,
                (value, context) -> {}
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
                    http.sendAsync(HttpRequest.newBuilder()
                                    .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(new AuthorizeUserRequest(
                                            context.getPlayer().getUUID(),
                                            value.state()
                                    ))))
                                    .uri(URI.create("https://maximum-honest-cat.ngrok-free.app/auth-server"))
                                    .build(), HttpResponse.BodyHandlers.ofString())
                            .handleAsync((response, throwable) -> {
                                if (throwable != null) {
                                    log.error("Failed to authorize user {}", context.getPlayer(), throwable);
                                } else {
                                    String secret = response.body();
                                    context.queue(() -> NetworkManager.sendToPlayer((ServerPlayer)context.getPlayer(), new ExchangeProvisionalSecret(secret)));
                                }

                                return null;
                            })
                            .thenRun(http::close);
                }
        );
    }

    public static void initDedicatedServer() {
        NetworkManager.registerS2CPayloadType(
                OpenUbercordGuildLinkScreenPacket.TYPE,
                OpenUbercordGuildLinkScreenPacket.STREAM_CODEC
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
    }
}
