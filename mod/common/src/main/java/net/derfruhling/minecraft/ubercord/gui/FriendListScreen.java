package net.derfruhling.minecraft.ubercord.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.background.SimpleColorBackground;
import dev.lambdaurora.spruceui.border.SimpleBorder;
import dev.lambdaurora.spruceui.screen.SpruceScreen;
import dev.lambdaurora.spruceui.widget.SpruceLabelWidget;
import dev.lambdaurora.spruceui.widget.container.SpruceEntryListWidget;
import dev.lambdaurora.spruceui.widget.text.SpruceTextAreaWidget;
import net.derfruhling.discord.socialsdk4j.ActivityInfo;
import net.derfruhling.discord.socialsdk4j.Message;
import net.derfruhling.discord.socialsdk4j.Relationship;
import net.derfruhling.discord.socialsdk4j.User;
import net.derfruhling.minecraft.ubercord.client.UbercordClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public final class FriendListScreen extends SpruceScreen implements HandlesNewMessage {
    private Relationship[] relationships = null;
    private SideFriendList friendList = null;
    private final SpruceLabelWidget title = new SpruceLabelWidget(Position.of(4, 4), Component.literal("übercord").withStyle(Style.EMPTY.withBold(true).withItalic(true)), 125);
    private final List<FriendContext> contexts = new LinkedList<>();
    private FriendContext currentContext = null;
    private SpruceTextAreaWidget textInput = null;

    public final long targetUserId;

    public FriendListScreen(long targetUserId) {
        super(Component.translatable("ubercord.friend_list.title"));
        this.targetUserId = targetUserId;
    }

    private void reallyReloadRelationships() {
        contexts.clear();
        currentContext = null;
        relationships = UbercordClient.get().getClient().getRelationships();
        friendList = new SideFriendList(relationships, this);
    }

    public void reloadRelationships() {
        reallyReloadRelationships();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();

        if(friendList == null) {
            reallyReloadRelationships();
        }

        if(textInput == null) {
            textInput = createTextInput();
        }

        addRenderableWidget(title);
        addRenderableWidget(friendList);
        addRenderableWidget(textInput);

        if(currentContext != null) {
            addRenderableWidget(currentContext.messages);
        }
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int i, int j) {
        friendList.setHeight(j - 28);
        textInput = createTextInput();

        if(currentContext != null) {
            currentContext.performResize(this);
        }

        super.resize(minecraft, i, j);
    }

    private @NotNull SpruceTextAreaWidget createTextInput() {
        SpruceTextAreaWidget textInput = new ChatInputWidget(this, Position.of(Position.of(133, height - 5), 0, -18), width - 137, 16, Component.literal("Send message"), Component.literal("Send message..."));
        textInput.setEditable(true);
        textInput.setBorder(SimpleBorder.SIMPLE_BORDER);
        textInput.setBackground(new SimpleColorBackground(0, 0, 0, 64));
        textInput.setDisplayedLines(1);
        return textInput;
    }

    public void ensureAvatarsAreReloaded(long userId, boolean isProvisional) {
        if(!isProvisional) return;

        for (FriendContext context : contexts) {
            if(context.targetUser.id == userId) {
                context.reloadAvatar();
            }
        }
    }

    private class SideFriendList extends SpruceEntryListWidget<FriendCategoryWidget> {
        private final List<FriendCategoryWidget> categoryWidgets;

        public SideFriendList(Relationship[] relationships, FriendListScreen friendListScreen) {
            super(Position.of(4, 20), 125, FriendListScreen.this.height - 28, 0, FriendCategoryWidget.class);

            /*replaceEntries(Arrays.stream(relationships)
                    .map(rel -> new FriendCardWidget(rel.user()))
                    .toList());*/

            FriendCategoryWidget inGame = new FriendCategoryWidget(Component.literal("In-game"), true);
            FriendCategoryWidget gameFriends = new FriendCategoryWidget(Component.literal("Game friends"), true);
            FriendCategoryWidget discordFriends = new FriendCategoryWidget(Component.literal("Discord friends"), true);
            FriendCategoryWidget everyoneElse = new FriendCategoryWidget(Component.literal("Everyone else"), false);
            FriendCategoryWidget blockedUsers = new FriendCategoryWidget(Component.literal("Blocked users"), false);

            for (Relationship rel : relationships) {
                User user = rel.user();
                FriendCardWidget widget = new FriendCardWidget(user, friendListScreen);
                if(rel.discordType() == Relationship.Type.Blocked || rel.gameType() == Relationship.Type.Blocked) {
                    blockedUsers.add(widget);
                } else {
                    assert user != null;
                    ActivityInfo activityInfo = user.getActivityInfo();

                    if(activityInfo != null && activityInfo.applicationId() == UbercordClient.get().getCurrentClientId()) {
                        inGame.add(widget);
                    } else if(rel.gameType() == Relationship.Type.Friend) {
                        gameFriends.add(widget);
                    } else if(rel.discordType() == Relationship.Type.Friend) {
                        discordFriends.add(widget);
                    } else {
                        everyoneElse.add(widget);
                    }
                }
            }

            categoryWidgets = Arrays.asList(inGame, gameFriends, discordFriends, everyoneElse, blockedUsers);
            replaceEntries(categoryWidgets);
        }

        @Override
        protected boolean onMouseClick(double mouseX, double mouseY, int button) {
            if(super.onMouseClick(mouseX, mouseY, button)) {
                replaceEntries(categoryWidgets);
                return true;
            } else return false;
        }

        public void setHeight(int height) {
            this.height = height;
        }
    }

    void registerContext(FriendContext context) {
        contexts.add(context);
    }

    void handleSwitchUserContext(FriendContext newContext) {
        currentContext = newContext;
        currentContext.performResize(this);
        rebuildWidgets();
    }

    void sendMessage(String message) {
        message = message.strip();
        ChatMessage msg = ChatMessage.fromSelf(this, currentContext, message);
        currentContext.addMessage(msg);

        UbercordClient.get().getClient().sendUserMessage(currentContext.targetUser.id, message, msg::updateStatus);
    }

    public @Nullable FriendContext getCurrentContext() {
        return currentContext;
    }

    @Override
    public void onNewUserMessage(User user, Message message) {
        for (FriendContext context : contexts) {
            context.onNewUserMessage(user, message);
        }
    }

    @Override
    public void onNewSelfMessageUnfocused(User target, Message message) {
        for (FriendContext context : contexts) {
            context.onNewSelfMessageUnfocused(target, message);
        }
    }
}
