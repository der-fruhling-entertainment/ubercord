package net.derfruhling.minecraft.ubercord.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.function.Function;

class MessageRef {
    protected final MutableComponent root;
    protected MutableComponent message;
    private boolean hasBeenEdited = false;
    private SocialSdkIntegration.MessageStatus status;

    public MessageRef(MutableComponent root, MutableComponent message, SocialSdkIntegration.MessageStatus status) {
        this.root = root;
        this.message = message;
        this.status = status;
    }

    public synchronized void changeStatus(SocialSdkIntegration.MessageStatus status) {
        this.status = status;

        MutableComponent orig = message;
        message = message.withStyle(status.style());

        root.getSiblings().set(root.getSiblings().indexOf(orig), message);
        Minecraft.getInstance().gui.getChat().rescaleChat();
    }

    public synchronized void edit(Function<MutableComponent, MutableComponent> editor) {
        if (!hasBeenEdited) {
            root.append(Component.literal(" ")
                    .append(Component.translatable("ubercord.disclosure.edited"))
                    .withStyle(Style.EMPTY
                            .withColor(ChatFormatting.DARK_GRAY)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("ubercord.disclosure.edited.desc")))));
            hasBeenEdited = true;
        }

        MutableComponent orig = message;
        message = editor.apply(message);

        if (orig != message) {
            root.getSiblings().set(root.getSiblings().indexOf(orig), message);

            Minecraft.getInstance().gui.getChat().rescaleChat();
        }
    }

    public void onDeleted() {
        if (hasBeenEdited) root.getSiblings().removeLast();
        root.append(Component.translatable("ubercord.disclosure.deleted")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.DARK_GRAY)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("ubercord.disclosure.deleted.desc")))));
        root.getSiblings().remove(message);
        Minecraft.getInstance().gui.getChat().rescaleChat();
    }
}
