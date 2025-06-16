package net.derfruhling.minecraft.ubercord.gui;

import net.derfruhling.discord.socialsdk4j.Message;
import net.derfruhling.discord.socialsdk4j.User;

public sealed interface HandlesNewMessage permits FriendListScreen, FriendContext {
    void onNewUserMessage(User user, Message message);
}
