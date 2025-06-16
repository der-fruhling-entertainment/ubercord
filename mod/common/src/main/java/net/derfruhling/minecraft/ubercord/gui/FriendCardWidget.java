package net.derfruhling.minecraft.ubercord.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.util.ColorUtil;
import dev.lambdaurora.spruceui.widget.AbstractSpruceWidget;
import dev.lambdaurora.spruceui.widget.container.SpruceEntryListWidget;
import net.derfruhling.discord.socialsdk4j.User;
import net.derfruhling.minecraft.ubercord.client.Badge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FriendCardWidget extends AbstractSpruceWidget {
    private static final Logger log = LoggerFactory.getLogger(FriendCardWidget.class);
    private final User user;
    private final FriendListScreen friendListScreen;
    private final FriendContext context;

    public FriendCardWidget(User user, FriendListScreen friendListScreen) {
        super(Position.origin());
        this.user = user;
        this.friendListScreen = friendListScreen;
        this.width = 125;
        this.height = 32;
        this.context = new FriendContext(user, new Avatar(Minecraft.getInstance(), user.id, user.getAvatar()), friendListScreen);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int x = position.getX();
        int y = position.getY();

        if(context.avatar.isReady()) {
            RenderSystem.enableBlend();

            if(friendListScreen.getCurrentContext() != null && friendListScreen.getCurrentContext().targetUser.id == user.id) {
                graphics.fill(RenderType.gui(), position.getX(), position.getY(), x + width - 8, y + height, 0x55aaaaaa);
                graphics.renderOutline(position.getX(), position.getY(), width - 8, height, ColorUtil.WHITE);
            }

            graphics.blit(context.avatar.resourceLocation, x + 4, y + 4, 0, 24, 24, 24, 24, 24, 24);
            RenderSystem.disableBlend();
        }

        graphics.drawString(client.font, user.getDisplayName(), x + 32, y + 4, 0xffffffff);
        graphics.drawString(client.font, switch(user.getRelationship().discordType()) {
            case Blocked -> Component.empty().append(Badge.BLOCKED_BADGE.create()).append("Blocked");
            case PendingIncoming -> Component.empty().append(Badge.INCOMING_FRIEND_REQUEST.create()).append("Friend request");
            case PendingOutgoing -> Component.empty().append(Badge.OUTGOING_FRIEND_REQUEST.create()).append("Sent friend request");
            default -> switch(user.getRelationship().gameType()) {
                case Blocked -> Component.empty().append(Badge.BLOCKED_BADGE.create()).append("Blocked");
                case PendingIncoming -> Component.empty().append(Badge.INCOMING_FRIEND_REQUEST.create()).append("Friend request");
                case PendingOutgoing -> Component.empty().append(Badge.OUTGOING_FRIEND_REQUEST.create()).append("Sent friend request");
                default -> switch(user.getStatus()) {
                    case Online -> Component.empty().append(Badge.ONLINE_BADGE.create()).append("Online");
                    case Offline, Invisible -> Component.empty().append(Badge.OFFLINE_BADGE.create()).append("Offline");
                    case Blocked -> Component.empty().append(Badge.BLOCKED_BADGE.create()).append("Blocked");
                    case Idle -> Component.empty().append(Badge.AWAY_BADGE.create()).append("Idle");
                    case DoNotDisturb -> Component.empty().append(Badge.DND_BADGE.create()).append("Do not disturb");
                    case Streaming -> Component.empty().append(Badge.STREAMING_BADGE.create()).append("Streaming");
                    case Unknown -> Component.empty().append(Badge.INVALID_BADGE.create()).append("Invalid status");
                };
            };
        }, x + 32, y + 20, 0xffaaaaaa);
    }

    @Override
    protected boolean onMouseClick(double mouseX, double mouseY, int button) {
        if(friendListScreen.getCurrentContext() == null || friendListScreen.getCurrentContext().targetUser.id != user.id) {
            friendListScreen.handleSwitchUserContext(context);
        }

        return true;
    }
}
