package net.derfruhling.minecraft.ubercord.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lambdaurora.spruceui.widget.container.SpruceEntryListWidget;
import net.derfruhling.discord.socialsdk4j.ClientResult;
import net.derfruhling.discord.socialsdk4j.Message;
import net.derfruhling.minecraft.ubercord.client.Badge;
import net.derfruhling.minecraft.ubercord.client.SocialSdkIntegration;
import net.derfruhling.minecraft.ubercord.client.UbercordClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ChatMessage extends SpruceEntryListWidget.Entry {
    private @Nullable Message messageHandle;
    private final Avatar avatar;
    private final @NotNull Component displayName, message;
    private Component metadata;
    private final FriendListScreen screen;
    private final FriendContext context;
    private final long author;
    private boolean shouldRenderAuthorInfo = false;
    private int standardMessageHeight;

    private enum Status {
        UNCONFIRMED,
        BROKEN,
        CONFIRMED
    }

    private Status status;

    private ChatMessage(@Nullable Message messageHandle, Avatar avatar, @NotNull Component displayName, @NotNull Component message, @Nullable Component metadata, FriendListScreen screen, FriendContext context, long author) {
        this.messageHandle = messageHandle;
        this.avatar = avatar;
        this.displayName = displayName;
        this.message = message;
        this.metadata = metadata;
        this.screen = screen;
        this.context = context;
        this.author = author;
        this.status = Status.UNCONFIRMED;
        performResize(screen);
    }

    public ChatMessage(FriendListScreen screen, FriendContext context, Message message) {
        avatar = context.avatar;
        // TODO IGN
        displayName = Component.empty()
                .append(context.targetUser.getDisplayName() + " ")
                .append(Badge.componentForUser(context.targetUser, null));

        // TODO parse discord markdown from raw content instead
        this.message = Component.literal(message.getContent());
        this.messageHandle = message;
        this.screen = screen;
        this.context = context;
        this.author = message.getAuthorId();

        if(message.getAdditionalContent() != null) {
            Message.AdditionalContent content = message.getAdditionalContent();
            metadata = switch(content.type()) {
                case Other -> Component.literal("[other attachment]");
                case Attachment -> Component.literal("[" + content.count() + " attachments]");
                case Poll -> Component.literal("[a poll: " + content.title() + "]");
                case VoiceMessage -> Component.literal("[a voice message]");
                case Thread -> Component.literal("[started a thread: " + content.title() + "]");
                case Embed -> Component.literal("[contains an embed]");
                case Sticker -> Component.literal("[contains a sticker]");
            };
        } else {
            metadata = null;
        }

        this.status = Status.CONFIRMED;

        performResize(screen);
    }

    static ChatMessage fromSelf(FriendListScreen screen, FriendContext context, String message) {
        SocialSdkIntegration i = UbercordClient.get();

        return new ChatMessage(
                null,
                i.getAvatar(),
                Component.empty()
                        .append(i.self.getDisplayName() + " ")
                        .append(Badge.componentForUser(i.self, null)),
                Component.literal(message),
                null,
                screen,
                context,
                i.self.id
        );
    }

    void performResize(FriendListScreen screen) {
        this.width = screen.width - 137;
        Font font = Minecraft.getInstance().font;
        int messageHeight = ComponentRenderUtils.wrapComponents(this.message, width - 32, font).size() * font.lineHeight;
        ChatMessage chatMessageBefore = context.messageBefore(this);
        shouldRenderAuthorInfo = chatMessageBefore == null || chatMessageBefore.author != author;

        this.standardMessageHeight = messageHeight + 2;
        if (shouldRenderAuthorInfo) {
            this.standardMessageHeight += 18;
        }

        if(this.metadata != null) {
            int metaHeight = ComponentRenderUtils.wrapComponents(this.metadata, width - 32, font).size() * font.lineHeight;

            this.height = this.standardMessageHeight + 9 + metaHeight;
        } else {
            this.height = this.standardMessageHeight;
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int x = getPosition().getX();
        int y = getPosition().getY();

        int messageColor = switch(status) {
            case UNCONFIRMED -> 0x55ffffff;
            case BROKEN -> 0xffff1111;
            case CONFIRMED -> 0xffdddddd;
        };

        if(shouldRenderAuthorInfo) {
            RenderSystem.enableBlend();
            graphics.blit(avatar.resourceLocation, x + 4, y + 4, 0, 24, 24, 24, 24, 24, 24);
            RenderSystem.disableBlend();

            graphics.drawString(client.font, displayName, x + 32, y + 4, 0xffffffff);
            graphics.drawWordWrap(client.font, message, x + 32, y + 16, width - 32, messageColor);

            if(metadata != null) {
                graphics.drawWordWrap(client.font, metadata, x + 32, y + 16 + standardMessageHeight + 9, width - 32, 0xff555555);
            }
        } else {
            graphics.drawWordWrap(client.font, message, x + 32, y + 4, width - 32, messageColor);

            if(metadata != null) {
                graphics.drawWordWrap(client.font, metadata, x + 32, y + 4 + standardMessageHeight + 9, width - 32, 0xff555555);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ChatMessage that = (ChatMessage) o;
        return author == that.author && shouldRenderAuthorInfo == that.shouldRenderAuthorInfo && standardMessageHeight == that.standardMessageHeight && Objects.equals(messageHandle, that.messageHandle) && Objects.equals(avatar, that.avatar) && Objects.equals(displayName, that.displayName) && Objects.equals(message, that.message) && Objects.equals(metadata, that.metadata) && Objects.equals(screen, that.screen) && Objects.equals(context, that.context) && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageHandle, avatar, displayName, message, metadata, screen, context, author, shouldRenderAuthorInfo, standardMessageHeight, status);
    }

    void updateStatus(ClientResult result, long messageId) {
        if(result.isSuccess()) {
            status = Status.CONFIRMED;
            this.messageHandle = UbercordClient.get().getClient().getMessage(messageId);
        } else {
            status = Status.BROKEN;
            metadata = Component.literal("> Message failed to send: " + result.message());
            performResize(screen);
        }
    }
}
