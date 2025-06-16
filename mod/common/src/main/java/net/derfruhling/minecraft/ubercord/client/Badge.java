package net.derfruhling.minecraft.ubercord.client;

import net.derfruhling.discord.socialsdk4j.Relationship;
import net.derfruhling.discord.socialsdk4j.User;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public enum Badge {
    OFFLINE_USER_MESSAGE("a", 0x7738ff, Badge::newOfflineUserComponent),
    ONLINE_USER_MESSAGE("", 0x3df261, Badge::newOnlineUserComponent),
    DISCORD("a", 0x7738ff),
    STATUS_MESSAGE("a", 0xffad29),
    YELLOW_EXCLAIM("c", 0xffad29, Badge::newOnlineUserComponent),
    RED_EXCLAIM("c", 0xff5555, Badge::newSystemUserComponent),
    REPLY_BUTTON("d",0x7738ff),
    ONLINE_BADGE("e", 0x4cef2f),
    AWAY_BADGE("f", 0xeef22e),
    DND_BADGE("g", 0xf24b2e),
    OFFLINE_BADGE("e", 0x848484),
    PLAYING_BADGE("h", 0x25c9f7),
    PLAYING_ELSEWHERE_BADGE("i", 0x1d8caa),
    STREAMING_BADGE("h", 0xc24efc),
    GAME_FRIEND_BADGE("j", 0xf537fc),
    DISCORD_FRIEND_BADGE("j", 0x7738ff),
    INCOMING_FRIEND_REQUEST("j", 0x3df261),
    OUTGOING_FRIEND_REQUEST("j", 0xd1d1d1),
    BLOCKED_BADGE("k", 0xfc2a38),
    UNKNOWN_USER_BADGE("?", 0xd1d1d1),
    INVALID_BADGE("?", 0xfc2a38),
    COLLAPSED_CATEGORY_BADGE(">", 0xffffff),
    OPENED_CATEGORY_BADGE("v", 0xffffff);

    private interface ComponentCreator {
        Component create(String ign, String discord, long discordId, @Nullable Relationship relationship);
    }

    private final Component component;
    private final @Nullable ComponentCreator creator;

    Badge(String s, int color, @Nullable ComponentCreator creator) {
        this.component = Component.literal(s)
                .withStyle(Style.EMPTY.withFont(UbercordClient.FONT).withColor(color));
        this.creator = creator;
    }

    Badge(String s, int color) {
        this(s, color, null);
    }

    private static Component newOnlineUserComponent(String ign, String discord, long discordId, @Nullable Relationship relationship) {
        return Component.literal(discord)
                .withStyle(Style.EMPTY
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, relationship != null
                            ? switch (relationship.gameType()) {
                                case Friend -> Component.translatable("ubercord.badge.online.hover.game.friend", ign);
                                case Blocked -> Component.translatable("ubercord.badge.online.hover.blocked", ign);
                                case PendingIncoming ->
                                        Component.translatable("ubercord.badge.online.hover.game.incoming_friend", ign);
                                case PendingOutgoing ->
                                        Component.translatable("ubercord.badge.online.hover.game.outgoing_friend", ign);
                                default -> switch (relationship.discordType()) {
                                    case Friend ->
                                            Component.translatable("ubercord.badge.online.hover.discord.friend", ign);
                                    case Blocked -> Component.translatable("ubercord.badge.online.hover.blocked", ign);
                                    case PendingIncoming ->
                                            Component.translatable("ubercord.badge.online.hover.discord.incoming_friend", ign);
                                    case PendingOutgoing ->
                                            Component.translatable("ubercord.badge.online.hover.discord.outgoing_friend", ign);
                                    default -> Component.translatable("ubercord.badge.online.hover.generic", ign);
                                };
                            }
                            : Component.translatable("ubercord.badge.online.hover.self")))
                        .withClickEvent(relationship != null
                            ? new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl user " + discordId)
                            : null));
    }

    // offline users always have a relationship since self can never be considered offline
    private static Component newOfflineUserComponent(String ign, String discord, long discordId, Relationship relationship) {
        return Component.literal(discord)
                .withStyle(Style.EMPTY
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, relationship != null
                        ? switch (relationship.gameType()) {
                            case Friend -> Component.translatable("ubercord.badge.offline.hover.game.friend");
                            case Blocked -> Component.translatable("ubercord.badge.offline.hover.blocked");
                            case PendingIncoming ->
                                    Component.translatable("ubercord.badge.offline.hover.game.incoming_friend");
                            case PendingOutgoing ->
                                    Component.translatable("ubercord.badge.offline.hover.game.outgoing_friend");
                            default -> switch (relationship.discordType()) {
                                case Friend -> Component.translatable("ubercord.badge.offline.hover.discord.friend");
                                case Blocked -> Component.translatable("ubercord.badge.offline.hover.blocked");
                                case PendingIncoming ->
                                        Component.translatable("ubercord.badge.offline.hover.discord.incoming_friend");
                                case PendingOutgoing ->
                                        Component.translatable("ubercord.badge.offline.hover.discord.outgoing_friend");
                                default -> Component.translatable("ubercord.badge.offline.hover.generic");
                            };
                        }
                        : Component.translatable("ubercord.badge.online.hover.self")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/chatctl user " + discordId)))
                .append(" ")
                .append(Component.literal("a")
                        .withStyle(Style.EMPTY
                                .withColor(0x7738ff)
                                .withFont(UbercordClient.FONT)
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("ubercord.badge.offline.hover_badge")))));
    }

    private static Component newSystemUserComponent(String ign, String discord, long discordId, @Nullable Relationship relationship) {
        return Component.literal(discord)
                .withStyle(Style.EMPTY
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("ubercord.badge.system.hover"))))
                .append(Component.literal("c")
                        .withStyle(Style.EMPTY
                                .withColor(ChatFormatting.RED)
                                .withFont(UbercordClient.FONT)
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("ubercord.badge.system.hover_badge")))));
    }

    public MutableComponent create() {
        return component.copy();
    }

    public Component create(String ign, String discord, long discordId, @Nullable Relationship relationship) {
        if (creator != null) {
            return creator.create(ign, discord, discordId, relationship);
        } else {
            return component;
        }
    }

    public static Badge[] forUser(User user) {
        Relationship relationship = user.getRelationship();
        List<Badge> badges = new ArrayList<>();

        Relationship.Type gameRel = relationship.gameType();
        switch (gameRel) {
            case Friend -> badges.add(Badge.GAME_FRIEND_BADGE);
            case Blocked -> {
                badges.add(Badge.BLOCKED_BADGE);
                return badges.toArray(new Badge[0]);
            }
            case PendingIncoming -> badges.add(Badge.INCOMING_FRIEND_REQUEST);
            case PendingOutgoing -> badges.add(Badge.OUTGOING_FRIEND_REQUEST);
            default -> {}
        }

        Relationship.Type discordRel = relationship.discordType();
        switch (discordRel) {
            case Friend -> {
                if(gameRel != Relationship.Type.Friend) {
                    badges.add(Badge.DISCORD_FRIEND_BADGE);
                }
            }
            case Blocked -> {
                badges.add(Badge.BLOCKED_BADGE);
                return badges.toArray(new Badge[0]);
            }
            case PendingIncoming -> badges.add(Badge.INCOMING_FRIEND_REQUEST);
            case PendingOutgoing -> badges.add(Badge.OUTGOING_FRIEND_REQUEST);
            default -> {}
        }

        return badges.toArray(new Badge[0]);
    }

    public static Component componentForUser(User user, String ign) {
        MutableComponent component = Component.empty();

        Relationship relationship = user.getRelationship();
        String displayName = user.getDisplayName();
        for (Badge badge : forUser(user)) {
            component.append(badge.create(ign, displayName, user.id, relationship));
        }

        return component;
    }
}
