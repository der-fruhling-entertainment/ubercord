package net.derfruhling.minecraft.ubercord.gui;

import dev.lambdaurora.spruceui.Position;
import net.derfruhling.discord.socialsdk4j.Message;
import net.derfruhling.discord.socialsdk4j.User;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class FriendContext implements HandlesNewMessage {
    public final User targetUser;
    public final Avatar avatar;
    final ChatMessageListWidget messages;
    private final List<ChatMessage> chatMessages = new ArrayList<>();
    private final FriendListScreen friendListScreen;

    public FriendContext(User targetUser, Avatar avatar, FriendListScreen screen) {
        this.targetUser = targetUser;
        this.avatar = avatar;
        this.messages = new ChatMessageListWidget(Position.of(133, 20), screen.width - 137, screen.height - 48, 0, ChatMessage.class);
        this.friendListScreen = screen;
        screen.registerContext(this);
    }

    @Override
    public void onNewUserMessage(User user, Message message) {
        if(user.id != targetUser.id) return;

        ChatMessage chatMessage = new ChatMessage(friendListScreen, this, message);
        chatMessages.add(chatMessage);
        messages.addMessage(chatMessage);
    }

    void addMessage(ChatMessage message) {
        chatMessages.add(message);
        messages.addMessage(message);
    }

    public void performResize(FriendListScreen screen) {
        for (ChatMessage chatMessage : chatMessages) {
            chatMessage.performResize(screen);
        }

        messages.setSize(screen.width - 137, screen.height - 48);
        messages.setScrollAmount(messages.getMaxScroll());
    }

    @Nullable ChatMessage messageBefore(ChatMessage message) {
        int index = chatMessages.indexOf(message);
        if(index == 0 || chatMessages.isEmpty()) return null;
        if(index == -1) return chatMessages.getLast();

        return chatMessages.get(index - 1);
    }
}
