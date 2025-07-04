package net.derfruhling.minecraft.ubercord.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

class StatusMessageRefTicker extends MessageRef {
    private MutableComponent animation;

    private int animationTicksRemaining = 0, animationFrame = -1;

    private StatusMessageRefTicker(MutableComponent root, MutableComponent message, MutableComponent animation) {
        super(root, message, SocialSdkIntegration.MessageStatus.CONFIRMED);
        this.animation = animation;
    }

    public synchronized void updateStatus(MutableComponent newStatus) {
        root.getSiblings().set(root.getSiblings().indexOf(message), newStatus);
        message = newStatus;
        Minecraft.getInstance().gui.getChat().rescaleChat();
    }

    public void succeed() {
        commonEndpoint(Component.literal("✔").withStyle(ChatFormatting.GREEN));
    }

    public void fail() {
        commonEndpoint(Component.literal("✕").withStyle(ChatFormatting.RED));
    }

    private synchronized void commonEndpoint(MutableComponent newMessage) {
        MutableComponent newAnimation = Component.empty();
        root.getSiblings().set(root.getSiblings().indexOf(animation), newAnimation);
        animation = newAnimation;

        root.getSiblings().set(root.getSiblings().indexOf(message), newMessage);
        message = newMessage;

        Minecraft.getInstance().gui.getChat().rescaleChat();
    }

    public synchronized void tick() {
        if (animationTicksRemaining-- == 0) {
            animationTicksRemaining = StatusMessage.ANIMATION_TICK_PERIOD;
            MutableComponent newComponent = StatusMessage.colorAnimation(StatusMessage.ANIMATION[++animationFrame % StatusMessage.ANIMATION.length]);
            root.getSiblings().set(root.getSiblings().indexOf(animation), newComponent);
            animation = newComponent;
            Minecraft.getInstance().gui.getChat().rescaleChat();
        }
    }

    public static StatusMessageRefTicker create(Component activity, Component status) {
        MutableComponent statusMut = status.copy();
        MutableComponent animation = Component.empty();
        MutableComponent root = activity.copy()
                .append(" ")
                .append(animation)
                .append(" ")
                .append(statusMut);

        Minecraft.getInstance().gui.getChat().addMessage(root, null, null);
        return new StatusMessageRefTicker(root, statusMut, animation);
    }
}
