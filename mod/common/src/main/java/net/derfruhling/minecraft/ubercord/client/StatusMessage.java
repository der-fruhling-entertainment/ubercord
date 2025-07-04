package net.derfruhling.minecraft.ubercord.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public class StatusMessage {
    public static final int ANIMATION_TICK_PERIOD = 3;
    public static final String[] ANIMATION = new String[]{
            "54321",
            "45432",
            "34543",
            "23454",
            "12345",
            "23454",
            "34543",
            "45432"
    };

    private int animationTicksRemaining = 0, animationFrame = -1;
    private Component animation = Component.empty(), statusInfo = Component.empty(), message = Component.empty();
    private boolean isActive = false, isDone = false;

    public boolean isActive() {
        return isActive;
    }

    public boolean isDone() {
        return isDone;
    }

    private static int colorForChar(char ch) {
        return switch (ch) {
            case '1' -> 0x4184ad;
            case '2' -> 0x4c9cce;
            case '3' -> 0x4faae2;
            case '4' -> 0x5dbaf4;
            case '5' -> 0xb3e0fc;
            default -> throw new IllegalStateException("Unexpected value: " + ch);
        };
    }

    static MutableComponent colorAnimation(String message) {
        MutableComponent component = Component.empty();

        for (char c : message.toCharArray()) {
            component.append(Component.literal(String.valueOf(c)).withColor(colorForChar(c)));
        }

        return component.withStyle(Style.EMPTY.withFont(UbercordClient.FONT));
    }


    public void succeed() {
        commonEndpoint(Component.literal("✔").withStyle(ChatFormatting.GREEN));
    }

    public void fail() {
        commonEndpoint(Component.literal("✕").withStyle(ChatFormatting.RED));
    }

    private synchronized void commonEndpoint(Component newMessage) {
        animation = Component.empty();
        statusInfo = newMessage;
        isDone = true;
    }

    public synchronized void tick() {
        if (!isActive || isDone) return;
        if (animationTicksRemaining-- == 0) {
            animationTicksRemaining = StatusMessage.ANIMATION_TICK_PERIOD;
            animation = StatusMessage.colorAnimation(StatusMessage.ANIMATION[++animationFrame % StatusMessage.ANIMATION.length]);
        }
    }

    public synchronized void reset(Component message, Component statusInfo) {
        this.message = message;
        this.statusInfo = statusInfo;
        this.animation = StatusMessage.colorAnimation(StatusMessage.ANIMATION[0]);
        this.animationFrame = 0;
        this.animationTicksRemaining = ANIMATION_TICK_PERIOD;
        this.isActive = true;
        this.isDone = false;
    }

    public int getAnimationTicksRemaining() {
        return animationTicksRemaining;
    }

    public int getAnimationFrame() {
        return animationFrame;
    }

    public Component getAnimation() {
        return animation;
    }

    public Component getStatusInfo() {
        return statusInfo;
    }

    public synchronized void setStatusInfo(Component statusInfo) {
        this.statusInfo = statusInfo.copy().withStyle(ChatFormatting.DARK_GRAY);
    }

    public Component getMessage() {
        return message;
    }

    public Component getFullComponent() {
        if(!isActive) {
            return Component.empty();
        } else {
            return Component.empty()
                    .append(message)
                    .append(" ")
                    .append(animation)
                    .append(" ")
                    .append(statusInfo);
        }
    }
}
