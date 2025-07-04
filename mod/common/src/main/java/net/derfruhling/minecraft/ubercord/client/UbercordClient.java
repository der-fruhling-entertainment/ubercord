package net.derfruhling.minecraft.ubercord.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.architectury.event.events.client.*;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent.ClientCommandSourceStack;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.derfruhling.discord.socialsdk4j.ClientResult;
import net.derfruhling.discord.socialsdk4j.Relationship;
import net.derfruhling.discord.socialsdk4j.SocialSdk;
import net.derfruhling.discord.socialsdk4j.User;
import net.derfruhling.discord.socialsdk4j.loader.ClasspathLoader;
import net.derfruhling.minecraft.ubercord.ManagedChannelKind;
import net.derfruhling.minecraft.ubercord.gui.FriendListScreen;
import net.derfruhling.minecraft.ubercord.gui.GuildSelectScreen;
import net.derfruhling.minecraft.ubercord.packets.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.mojang.brigadier.arguments.LongArgumentType.longArg;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static dev.architectury.event.events.client.ClientCommandRegistrationEvent.literal;
import static dev.architectury.event.events.client.ClientCommandRegistrationEvent.argument;

public final class UbercordClient {
    private static final Logger log = LogManager.getLogger(UbercordClient.class);
    private static SocialSdkIntegration integration;
    private static boolean clothConfigPresent = false;
    private static final StatusMessage statusTicker = new StatusMessage();
    private static boolean canAddBeforeTitleScreens = true;
    private static final List<Function<Runnable, Screen>> screensToShowBeforeTitle = new LinkedList<>();

    public static StatusMessage getStatus() {
        return statusTicker;
    }

    public static void show(Function<Runnable, Screen> screen) {
        if(canAddBeforeTitleScreens) {
            screensToShowBeforeTitle.add(screen);
        } else {
            Minecraft instance = Minecraft.getInstance();
            Screen orig = instance.screen;
            instance.setScreen(screen.apply(() -> instance.setScreen(orig)));
        }
    }

    @SuppressWarnings("unchecked")
    public static Function<Runnable, Screen>[] consumeScreensToShowBeforeTitle() {
        var array = screensToShowBeforeTitle.toArray(new Function[0]);
        screensToShowBeforeTitle.clear();
        canAddBeforeTitleScreens = false;
        return array;
    }

    static FriendListScreen friendListScreen = null;
    
    public static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("ubercord", "font");

    public static SocialSdkIntegration get() {
        return integration;
    }

    public static void setClothConfigPresent(boolean clothConfigPresent) {
        UbercordClient.clothConfigPresent = clothConfigPresent;
    }

    public static final KeyMapping FRIENDS_LIST_KEYMAPPING = new KeyMapping("key.ubercord.friend_list", InputConstants.Type.KEYSYM, InputConstants.KEY_GRAVE, "category.ubercord");

    public static void init() {
        SocialSdk.initialize(new ClasspathLoader());
        SocialSdk.setLogCallback((level, s) -> {
            if(level.isMoreSpecificThan(Level.INFO)) {
                log.log(level, s);
            }
        });

        integration = new SocialSdkIntegration();

        KeyMappingRegistry.register(FRIENDS_LIST_KEYMAPPING);

        NetworkManager.registerReceiver(
                NetworkManager.s2c(),
                NotifyAboutUserId.TYPE,
                NotifyAboutUserId.STREAM_CODEC,
                (value, context) -> integration.updatePlayer(value)
        );

        NetworkManager.registerReceiver(
                NetworkManager.s2c(),
                LobbyIdFound.TYPE,
                LobbyIdFound.STREAM_CODEC,
                (value, context) -> {
                    integration.joinServerLobby(value.lobbyId(), value.name(), value.isUsingCustomService());
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.s2c(),
                LobbyError.TYPE,
                LobbyError.STREAM_CODEC,
                (value, context) -> {
                    integration.lobbyJoinFailed(value.lobbyName());
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.s2c(),
                DeclareServerConfig.TYPE,
                DeclareServerConfig.STREAM_CODEC,
                (value, context) -> {
                    integration.setServerConfig(value.config());

                    updatePlayingRichPresence();
                }
        );

        ClientCommandRegistrationEvent.EVENT.register((dispatcher, context) -> {
            var dm = argument("user", new DiscordUserArgument())
                    .then(argument("message", greedyString())
                            .executes(UbercordClient::onDmAction));

            dispatcher.register(literal("dm")
                    .requires(stack -> integration.isChatFeaturesEnabled())
                    .then(dm));

            dispatcher.register(literal("channel")
                    .requires(stack -> integration.isChatFeaturesEnabled())
                    .then(literal("join")
                            .then(literal("global")
                                    .then(argument("name", word())
                                            .executes(UbercordClient::onGlobalJoinAction)))
                            .then(argument("name", word())
                                    .executes(UbercordClient::onJoinAction)))
                    .then(literal("leave")
                            .then(argument("name", word())
                                    .suggests(UbercordClient::suggestLobbyName)
                                    .executes(UbercordClient::onLeaveAction)))
                    .then(literal("id-of")
                            .then(argument("name", word())
                                    .suggests(UbercordClient::suggestLobbyName)
                                    .executes(UbercordClient::onGetIdAction)))
                    .then(argument("name", word())
                            .executes(UbercordClient::onSwitchAction)));

            dispatcher.register(literal("chatctl")
                    .executes(ctx -> {
                        integration.getClient().openConnectedGameSettingsInDiscord(result -> {
                            if(!result.isSuccess()) {
                                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.discord.connected_game_settings.failed").withStyle(ChatFormatting.RED));
                            }
                        });

                        return 0;
                    })
                    .then(literal("config")
                            .requires(stack -> clothConfigPresent)
                            .executes(ctx -> {
                                Minecraft.getInstance().tell(() -> Minecraft.getInstance().setScreen(ConfigFactory.create(null, integration.getConfig())));
                                return 0;
                            }))
                    .then(literal("authorize")
                            .requires(stack -> integration.isChatFeaturesEnabled())
                            .then(literal("discord")
                                    .executes(UbercordClient::authorizeDiscordAction)
                                    .then(argument("client", longArg())
                                            .executes(UbercordClient::authorizeDiscordWithClientIdAction)))
                            .then(literal("provisional")
                                    .executes(UbercordClient::authorizeProvisionalAction)
                                    .then(argument("client", longArg())
                                            .executes(UbercordClient::authorizeProvisionalWithClientIdAction)))
                            .then(literal("try-provisional")
                                    .executes(UbercordClient::authorizeTryProvisionalAction)))
                    .then(literal("block")
                            .requires(stack -> integration.isChatFeaturesEnabled())
                            .then(literal("add")
                                    .then(argument("id", longArg())
                                            .executes(UbercordClient::addBlockAction)))
                            .then(literal("remove")
                                    .then(argument("id", longArg())
                                            .executes(UbercordClient::removeBlockAction))))
                    .then(literal("game-friends")
                            .requires(stack -> integration.isChatFeaturesEnabled())
                            .then(literal("add")
                                    .then(argument("id", longArg())
                                            .executes(UbercordClient::addGameFriendAction)))
                            .then(literal("remove")
                                    .then(argument("id", longArg())
                                            .executes(UbercordClient::tryRemoveGameFriendAction)
                                            .then(literal("confirm")
                                                    .executes(UbercordClient::removeGameFriendAction))))
                            .then(literal("reject")
                                    .then(argument("id", longArg())
                                            .executes(UbercordClient::rejectGameFriendRequestAction)))
                            .then(literal("accept")
                                    .then(argument("id", longArg())
                                            .executes(UbercordClient::acceptGameFriendRequestAction)))
                            .then(literal("cancel")
                                    .then(argument("id", longArg())
                                            .executes(UbercordClient::cancelGameFriendRequestAction))))
                    .then(literal("discord-friends")
                            .requires(stack -> integration.isChatFeaturesEnabled())
                            .then(literal("add")
                                    .then(argument("id", longArg())
                                            .executes(UbercordClient::addDiscordFriendAction)))
                            .then(literal("remove")
                                    .then(argument("id", longArg())
                                            .executes(UbercordClient::tryRemoveDiscordFriendAction)
                                            .then(literal("confirm")
                                                .executes(UbercordClient::removeDiscordFriendAction))))
                            .then(literal("reject")
                                    .then(argument("id", longArg())
                                            .executes(UbercordClient::rejectDiscordFriendRequestAction)))
                            .then(literal("accept")
                                    .then(argument("id", longArg())
                                            .executes(UbercordClient::acceptDiscordFriendRequestAction)))
                            .then(literal("cancel")
                                    .then(argument("id", longArg())
                                            .executes(UbercordClient::cancelDiscordFriendRequestAction))))
                    .then(literal("user")
                            .requires(stack -> integration.isChatFeaturesEnabled())
                            .then(argument("id", longArg())
                                    .executes(UbercordClient::onGetUserInfoAction))));

            dispatcher.register(literal("friend")
                    .requires(stack -> integration.isChatFeaturesEnabled())
                    .then(literal("player")
                            .then(argument("player", word())
                                    .suggests(UbercordClient::getOnlinePlayerNameSuggestions)
                                    .executes(UbercordClient::onFriendPlayerAction)))
                    .then(literal("discord")
                            .then(literal("by-name")
                                    .then(argument("username", word())
                                            .executes(UbercordClient::onFriendDiscordByNameAction)))
                            .then(literal("player")
                                    .then(argument("player", EntityArgument.player())
                                            .suggests(UbercordClient::getOnlinePlayerNameSuggestions)
                                            .executes(UbercordClient::onFriendDiscordPlayerAction)))));

            dispatcher.register(literal("invite")
                    .requires(stack -> !Minecraft.getInstance().isSingleplayer())
                    .then(argument("player", new DiscordUserArgument())
                            .executes(ctx -> {
                                long userId = ctx.getArgument("player", Long.class);
                                integration.getClient().sendActivityInvite(userId, null, result -> {
                                    if (result.isSuccess()) {
                                        ctx.getSource().arch$sendSuccess(() -> Component.translatable("ubercord.invite.success"), false);
                                    } else {
                                        ctx.getSource().arch$sendFailure(Component.translatable("ubercord.invite.error", result.message()));
                                    }
                                });

                                return 0;
                            })));

            dispatcher.register(literal("link")
                    .requires(stack -> stack.hasPermission(2))
                    .executes(ctx -> {
                        if (integration.getCurrentChannel() != null) {
                            Minecraft.getInstance().tell(() -> {
                                Minecraft.getInstance().setScreen(new GuildSelectScreen());
                            });
                            return 0;
                        } else {
                            ctx.getSource().arch$sendFailure(Component.translatable("ubercord.link.no_channel_selected_error"));
                            return 1;
                        }
                    }));
        });

        ClientTickEvent.CLIENT_POST.register(client -> {
            while (FRIENDS_LIST_KEYMAPPING.consumeClick()) {
                FriendListScreen friendsListScreen = getFriendsListScreen();
                if(friendsListScreen == null) return;

                client.setScreen(friendListScreen);
            }

            get().tick();
        });

        ClientLifecycleEvent.CLIENT_STARTED.register(client -> {
            integration.connect();
            integration.updateRichPresenceOnTitleScreen();
        });
        ClientLifecycleEvent.CLIENT_STOPPING.register(client -> integration.disconnect());
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
            if(integration.isReady()) {
                NetworkManager.sendToServer(new SetUserIdPacket(integration.self.id, integration.self.isProvisional()));
            }

            updatePlayingRichPresence();
        });
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            integration.leaveServer();
            integration.updateRichPresenceOnTitleScreen();
        });

        AtomicInteger ticks = new AtomicInteger(0);
        ClientTickEvent.CLIENT_POST.register(client -> {
            if(ticks.getAndIncrement() >= 100) {
                if(Minecraft.getInstance().getCurrentServer() != null || Minecraft.getInstance().player != null) {
                    updatePlayingRichPresence();
                } else {
                    integration.updateRichPresenceOnTitleScreen();
                }
                ticks.set(0);
            }

            integration.getClient().runCallbacks();
        });
    }

    static @Nullable FriendListScreen getFriendsListScreen() {
        if(!get().isReady()) return null;

        if(friendListScreen == null || friendListScreen.targetUserId != get().self.id) {
            friendListScreen = new FriendListScreen(get().self.id);
        }

        return friendListScreen;
    }

    private static void updatePlayingRichPresence() {
        if(Minecraft.getInstance().isSingleplayer()) {
            integration.updateRichPresenceOnPlayingSingleplayer();
        } else {
            integration.updateRichPresenceOnPlayingMultiplayer();
        }
    }

    private static int onFriendPlayerAction(CommandContext<ClientCommandSourceStack> ctx) throws CommandSyntaxException {
        String playerName = ctx.getArgument("player", String.class);
        integration.sendGameFriendRequest(playerName)
                .handle((result, throwable) -> handleFriendRequestCallback(ctx, result, throwable, playerName));;

        return 0;
    }

    private static int onFriendDiscordByNameAction(CommandContext<ClientCommandSourceStack> ctx) {
        String username = ctx.getArgument("username", String.class);

        integration.getClient().sendDiscordFriendRequest(username, result -> {
            if(result.isSuccess()) {
                ctx.getSource().arch$sendSuccess(() -> Component.translatable("ubercord.friend.request.successful", "@" + username), false);
            } else {
                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.friend.request.failed.error", "@" + username, result.message()));
            }
        });

        return 0;
    }

    private static int onFriendDiscordPlayerAction(CommandContext<ClientCommandSourceStack> ctx) {
        String playerName = ctx.getArgument("player", String.class);
        integration.sendDiscordFriendRequest(playerName)
                .handle((result, throwable) -> handleFriendRequestCallback(ctx, result, throwable, playerName));

        return 0;
    }

    private static @Nullable Void handleFriendRequestCallback(CommandContext<ClientCommandSourceStack> ctx, ClientResult result, Throwable throwable, String playerName) {
        if(throwable != null) {
            ctx.getSource().arch$sendFailure(Component.translatable("ubercord.friend.request.failed.exception", playerName, throwable.toString()));
        } else {
            if(result.isSuccess()) {
                ctx.getSource().arch$sendSuccess(() -> Component.translatable("ubercord.friend.request.successful", playerName), false);
            } else {
                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.friend.request.failed.error", playerName, result.message()));
            }
        }

        return null;
    }

    private static CompletableFuture<Suggestions> suggestLobbyName(CommandContext<ClientCommandSourceStack> ctx, SuggestionsBuilder builder) {
        integration.getJoinedChannels().forEach(entry -> {
            builder.suggest(entry.getKey());
        });

        return builder.buildFuture();
    }

    private static int onDmAction(CommandContext<ClientCommandSourceStack> ctx) {
        Long userId = ctx.getArgument("user", Long.class);
        String message = ctx.getArgument("message", String.class);

        User user = get().getClient().getUser(userId);
        SocialSdkIntegration.StatusChanger ref = get().generateSentDmMessage(user, message);

        integration.getClient().sendUserMessage(userId, message, (result, messageId) -> {
            if (!result.isSuccess()) {
                ref.invoke(false, 0);
                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.generic.error", result.message()));
            } else {
                ref.invoke(true, messageId);
            }
        });

        return 0;
    }

    private static int onJoinAction(CommandContext<ClientCommandSourceStack> ctx) {
        String name = ctx.getArgument("name", String.class);
        integration.joinLobby(ManagedChannelKind.SERVER, name);
        return 0;
    }

    private static int onGlobalJoinAction(CommandContext<ClientCommandSourceStack> ctx) {
        String name = ctx.getArgument("name", String.class);
        integration.joinLobby(ManagedChannelKind.GLOBAL, name);
        return 0;
    }

    private static int onLeaveAction(CommandContext<ClientCommandSourceStack> ctx) {
        String name = ctx.getArgument("name", String.class);
        integration.leaveLobby(name);
        return 0;
    }

    private static int onSwitchAction(CommandContext<ClientCommandSourceStack> ctx) {
        integration.setChannel(ctx.getArgument("name", String.class));
        return 0;
    }

    private static int onGetIdAction(CommandContext<ClientCommandSourceStack> ctx) {
        JoinedChannel lobby = integration.getJoinedChannel(ctx.getArgument("name", String.class));

        if(lobby == null) {
            ctx.getSource().arch$sendFailure(Component.translatable("ubercord.lobby.not_found_error"));
            return 1;
        } else {
            ctx.getSource().arch$sendSuccess(() ->
                    Component.literal(Long.toString(lobby.lobbyId()))
                            .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, Long.toString(lobby.lobbyId())))), false);
            return 0;
        }
    }

    private static CompletableFuture<Suggestions> getOnlinePlayerNameSuggestions(CommandContext<ClientCommandSourceStack> context1, SuggestionsBuilder builder) {
        for (String name : integration.getOnlinePlayers()) {
            builder.suggest(name);
        }

        return builder.buildFuture();
    }

    private static int onGetUserInfoAction(CommandContext<ClientCommandSourceStack> ctx) {
        User user = integration.getClient().getUser(ctx.getArgument("id", Long.class));

        if (user == null) {
            ctx.getSource().arch$sendFailure(Component.translatable("ubercord.error.failed_get_user"));
            return 1;
        } else {
            MutableComponent component = Component.empty();
            component.append(Component.translatable("ubercord.user.unique_name", user.getUsername())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            component.append("\n");

            if (user.isProvisional()) {
                component.append(Component.translatable("ubercord.user.provisional")
                        .withStyle(ChatFormatting.GRAY));
                component.append("\n");
            }

            component.append(Component.literal(user.getDisplayName()));

            if(user.id != integration.self.id) {
                component.append("\n\n");

                Relationship r = user.getRelationship();

                if (r.gameType() == Relationship.Type.Blocked) {
                    component.append(Component.translatable("ubercord.user.remove_block")
                            .withStyle(Style.EMPTY
                                    .withColor(ChatFormatting.RED)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl block remove " + user.id))));
                    component.append("\n");
                } else {
                    switch (r.gameType()) {
                        case None -> {
                            component.append(Component.translatable("ubercord.user.add_game_friend")
                                    .withStyle(Style.EMPTY
                                            .withColor(ChatFormatting.BLUE)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl game-friends add " + user.id))));
                            component.append("\n");
                        }
                        case PendingIncoming -> {
                            component.append(Component.translatable("ubercord.user.game_friend_request"));
                            component.append(Component.translatable("ubercord.notification.friend_request.new.accept")
                                    .withStyle(Style.EMPTY
                                            .withColor(ChatFormatting.GREEN)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl game-friends accept " + user.id))));
                            component.append(" ");
                            component.append(Component.translatable("ubercord.notification.friend_request.new.reject")
                                    .withStyle(Style.EMPTY
                                            .withColor(ChatFormatting.RED)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl game-friends reject " + user.id))));
                            component.append("\n");
                        }
                        case PendingOutgoing -> {
                            component.append(Component.translatable("ubercord.user.game_friend_request"));
                            component.append(Component.translatable("ubercord.user.cancel_outgoing_friend")
                                    .withStyle(Style.EMPTY
                                            .withColor(ChatFormatting.RED)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl game-friends cancel " + user.id))));
                            component.append("\n");
                        }
                        case Friend -> {
                            component.append(Component.translatable("ubercord.user.game_friend"));
                            component.append(Component.translatable("ubercord.user.remove_game_friend")
                                    .withStyle(Style.EMPTY
                                            .withColor(ChatFormatting.RED)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl game-friends remove " + user.id))));
                            component.append("\n");
                        }
                        default -> {
                        }
                    }

                    if (!integration.self.isProvisional() || !user.isProvisional()) {
                        switch (r.discordType()) {
                            case None -> {
                                component.append(Component.translatable("ubercord.user.add_discord_friend")
                                        .withStyle(Style.EMPTY
                                                .withColor(ChatFormatting.BLUE)
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl discord-friends add " + user.id))));
                                component.append("\n");
                            }
                            case PendingIncoming -> {
                                component.append(Component.translatable("ubercord.user.discord_friend_request"));
                                component.append(Component.translatable("ubercord.notification.friend_request.new.accept")
                                        .withStyle(Style.EMPTY
                                                .withColor(ChatFormatting.GREEN)
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl discord-friends accept " + user.id))));
                                component.append(" ");
                                component.append(Component.translatable("ubercord.notification.friend_request.new.reject")
                                        .withStyle(Style.EMPTY
                                                .withColor(ChatFormatting.RED)
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl discord-friends reject " + user.id))));
                                component.append("\n");
                            }
                            case PendingOutgoing -> {
                                component.append(Component.translatable("ubercord.user.discord_friend_request"));
                                component.append(Component.translatable("ubercord.user.cancel_outgoing_friend")
                                        .withStyle(Style.EMPTY
                                                .withColor(ChatFormatting.RED)
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl discord-friends cancel " + user.id))));
                                component.append("\n");
                            }
                            case Friend -> {
                                component.append(Component.translatable("ubercord.user.discord_friend"));
                                component.append(Component.translatable("ubercord.user.remove_discord_friend")
                                        .withStyle(Style.EMPTY
                                                .withColor(ChatFormatting.RED)
                                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl discord-friends remove " + user.id))));
                                component.append("\n");
                            }
                            default -> {
                            }
                        }
                    }

                    component.append(Component.translatable("ubercord.user.add_block")
                            .withStyle(Style.EMPTY
                                    .withColor(ChatFormatting.RED)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl block add " + user.id))));
                    component.append("\n");

                    if(r.gameType() == Relationship.Type.Friend || r.discordType() == Relationship.Type.Friend) {
                        component.append(Component.translatable("ubercord.user.dm")
                                .withStyle(Style.EMPTY
                                        .withColor(ChatFormatting.BLUE)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/dm " + user.id + " "))));
                        component.append("\n");
                    }
                }
            }

            Minecraft.getInstance().gui.getChat().addMessage(component, null, null);
            return 0;
        }
    }

    private static int authorizeDiscordAction(CommandContext<ClientCommandSourceStack> context1) {
        integration.authorizeReal(integration.defaultClientId, false);
        return 0;
    }

    private static int authorizeDiscordWithClientIdAction(CommandContext<ClientCommandSourceStack> ctx) {
        long clientId = ctx.getArgument("client", Long.class);
        integration.authorizeReal(clientId, true);
        return 0;
    }

    private static int authorizeProvisionalAction(CommandContext<ClientCommandSourceStack> context1) {
        integration.authorizeProvisional(integration.defaultClientId);
        return 0;
    }

    private static int authorizeTryProvisionalAction(CommandContext<ClientCommandSourceStack> context1) {
        if(integration.hasAgreedToProvisionalDisclaimer()) {
            integration.authorizeProvisional(integration.defaultClientId);
        } else {
            integration.printProvisionalDisclaimer();
        }

        return 0;
    }

    private static int authorizeProvisionalWithClientIdAction(CommandContext<ClientCommandSourceStack> ctx) {
        long clientId = ctx.getArgument("client", Long.class);
        integration.authorizeProvisional(clientId);
        return 0;
    }

    private static int addBlockAction(CommandContext<ClientCommandSourceStack> ctx) {
        integration.getClient().blockUser(ctx.getArgument("id", Long.class), result -> {
            if (result.isSuccess()) {
                ctx.getSource().arch$sendSuccess(() -> Component.translatable("ubercord.friend.block.add"), false);
            } else {
                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.friend.block.add.failed", result.message()));
            }
        });

        return 0;
    }

    private static int removeBlockAction(CommandContext<ClientCommandSourceStack> ctx) {
        integration.getClient().unblockUser(ctx.getArgument("id", Long.class), result -> {
            if (result.isSuccess()) {
                ctx.getSource().arch$sendSuccess(() -> Component.translatable("ubercord.friend.block.remove"), false);
            } else {
                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.friend.block.remove.failed", result.message()));
            }
        });

        return 0;
    }

    private static int addGameFriendAction(CommandContext<ClientCommandSourceStack> ctx) {
        integration.getClient().sendGameFriendRequest(ctx.getArgument("id", Long.class), result -> {
            if (result.isSuccess()) {
                ctx.getSource().arch$sendSuccess(() -> Component.translatable("ubercord.friend.game_friends.add"), false);
            } else {
                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.friend.game_friends.add.failed", result.message()));
            }
        });

        return 0;
    }

    private static int tryRemoveGameFriendAction(CommandContext<ClientCommandSourceStack> ctx) {
        long id = ctx.getArgument("id", Long.class);
        User user = integration.getClient().getUser(id);
        String name = user != null ? user.getDisplayName() : "<null>";
        ctx.getSource().arch$sendSuccess(() -> Component
                .translatable("ubercord.friend.game_friends.remove.confirm", name)
                .append("\n\n")
                .append(Component.translatable("ubercord.friend.confirm")
                        .withStyle(Style.EMPTY
                                .withColor(ChatFormatting.RED)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl game-friends remove " + id + " confirm")))), false);
        return 0;
    }

    private static int removeGameFriendAction(CommandContext<ClientCommandSourceStack> ctx) {
        integration.getClient().removeGameFriend(ctx.getArgument("id", Long.class), result -> {
            if (result.isSuccess()) {
                ctx.getSource().arch$sendSuccess(() -> Component.translatable("ubercord.friend.game_friends.remove"), false);
            } else {
                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.friend.game_friends.remove.failed", result.message()));
            }
        });

        return 0;
    }

    private static int rejectGameFriendRequestAction(CommandContext<ClientCommandSourceStack> ctx) {
        integration.getClient().rejectGameFriendRequest(ctx.getArgument("id", Long.class), result -> {
            if (result.isSuccess()) {
                ctx.getSource().arch$sendSuccess(() -> Component.translatable("ubercord.friend.game_friends.reject"), false);
            } else {
                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.friend.game_friends.reject.failed", result.message()));
            }
        });

        return 0;
    }

    private static int acceptGameFriendRequestAction(CommandContext<ClientCommandSourceStack> ctx) {
        long id = ctx.getArgument("id", Long.class);
        User user = integration.getClient().getUser(id);
        String name = user != null ? user.getDisplayName() : "<null>";
        integration.getClient().acceptGameFriendRequest(id, result -> {
            if (result.isSuccess()) {
                ctx.getSource().arch$sendSuccess(() -> Component.translatable("ubercord.friend.game_friends.accept", name), false);
            } else {
                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.friend.game_friends.accept.failed", result.message()));
            }
        });

        return 0;
    }

    private static int cancelGameFriendRequestAction(CommandContext<ClientCommandSourceStack> ctx) {
        integration.getClient().cancelGameFriendRequest(ctx.getArgument("id", Long.class), result -> {
            if (result.isSuccess()) {
                ctx.getSource().arch$sendSuccess(() -> Component.translatable("ubercord.friend.game_friends.cancel"), false);
            } else {
                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.friend.game_friends.cancel.failed", result.message()));
            }
        });

        return 0;
    }

    private static int addDiscordFriendAction(CommandContext<ClientCommandSourceStack> ctx) {
        integration.getClient().sendDiscordFriendRequest(ctx.getArgument("id", Long.class), result -> {
            if (result.isSuccess()) {
                ctx.getSource().arch$sendSuccess(() -> Component.translatable("ubercord.friend.discord_friends.add"), false);
            } else {
                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.friend.discord_friends.add.failed", result.message()));
            }
        });

        return 0;
    }

    private static int tryRemoveDiscordFriendAction(CommandContext<ClientCommandSourceStack> ctx) {
        long id = ctx.getArgument("id", Long.class);
        User user = integration.getClient().getUser(id);
        String name = user != null ? user.getDisplayName() : "<null>";
        ctx.getSource().arch$sendSuccess(() -> Component
                .translatable("ubercord.friend.discord_friends.remove.confirm", name)
                .append("\n\n")
                .append(Component.translatable("ubercord.friend.confirm")
                        .withStyle(Style.EMPTY
                                .withColor(ChatFormatting.RED)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl discord-friends remove " + id + " confirm")))), false);
        return 0;
    }

    private static int removeDiscordFriendAction(CommandContext<ClientCommandSourceStack> ctx) {
        integration.getClient().removeDiscordAndGameFriend(ctx.getArgument("id", Long.class), result -> {
            if (result.isSuccess()) {
                ctx.getSource().arch$sendSuccess(() -> Component.translatable("ubercord.friend.discord_friends.remove"), false);
            } else {
                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.friend.discord_friends.remove.failed", result.message()));
            }
        });

        return 0;
    }

    private static int rejectDiscordFriendRequestAction(CommandContext<ClientCommandSourceStack> ctx) {
        integration.getClient().rejectDiscordFriendRequest(ctx.getArgument("id", Long.class), result -> {
            if (result.isSuccess()) {
                ctx.getSource().arch$sendSuccess(() -> Component.translatable("ubercord.friend.discord_friends.reject"), false);
            } else {
                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.friend.discord_friends.reject.failed", result.message()));
            }
        });

        return 0;
    }

    private static int acceptDiscordFriendRequestAction(CommandContext<ClientCommandSourceStack> ctx) {
        long id = ctx.getArgument("id", Long.class);
        integration.getClient().acceptDiscordFriendRequest(id, result -> {
            if (result.isSuccess()) {
                User user = integration.getClient().getUser(id);
                String name = user != null ? user.getDisplayName() : "<null>";
                ctx.getSource().arch$sendSuccess(() -> Component.translatable("ubercord.friend.discord_friends.accept", name), false);
            } else {
                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.friend.discord_friends.accept.failed", result.message()));
            }
        });

        return 0;
    }

    private static int cancelDiscordFriendRequestAction(CommandContext<ClientCommandSourceStack> ctx) {
        integration.getClient().cancelDiscordFriendRequest(ctx.getArgument("id", Long.class), result -> {
            if (result.isSuccess()) {
                ctx.getSource().arch$sendSuccess(() -> Component.translatable("ubercord.friend.discord_friends.cancel"), false);
            } else {
                ctx.getSource().arch$sendFailure(Component.translatable("ubercord.friend.discord_friends.cancel.failed", result.message()));
            }
        });

        return 0;
    }
}
