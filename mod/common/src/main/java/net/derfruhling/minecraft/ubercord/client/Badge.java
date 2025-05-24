package net.derfruhling.minecraft.ubercord.client;

import net.derfruhling.discord.socialsdk4j.Relationship;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import org.jetbrains.annotations.Nullable;

public enum Badge {
    @SuppressWarnings("DataFlowIssue")
    OFFLINE_USER("a", 0x7738ff, Badge::newOfflineUserComponent),
    DISCORD("a", 0x7738ff),
    YELLOW_DOT("b", 0xffad29),
    ONLINE_USER("b", 0x3df261, Badge::newOnlineUserComponent),
    YELLOW_EXCLAIM("c", 0xffad29, Badge::newOnlineUserComponent),
    RED_EXCLAIM("c", 0xff5555, Badge::newSystemUserComponent),
    REPLY_BUTTON("d",0x7738ff);

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
}
