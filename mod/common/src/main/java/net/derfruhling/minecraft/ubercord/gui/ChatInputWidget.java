package net.derfruhling.minecraft.ubercord.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.widget.text.SpruceTextAreaWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class ChatInputWidget extends SpruceTextAreaWidget {
    private final FriendListScreen parent;

    public ChatInputWidget(FriendListScreen parent, Position position, int width, int height, Component title, Component placeholder) {
        super(position, width, height, title, placeholder);
        this.parent = parent;
    }

    @Override
    protected boolean onCharTyped(char chr, int keyCode) {
        boolean b = super.onCharTyped(chr, keyCode);
        int displayedLines = Math.min(Math.max(1, getLines().size()), 5);
        setDisplayedLines(displayedLines);
        height = 7 + displayedLines * client.font.lineHeight;
        position.setRelativeY(-9 - (displayedLines * client.font.lineHeight));
        return b;
    }

    @Override
    protected boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        if((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            if(!Screen.hasShiftDown()) {
                parent.sendMessage(getText());
                clear();
                setDisplayedLines(1);
                height = 7 + client.font.lineHeight;
                position.setRelativeY(-9 - client.font.lineHeight);
                return true;
            } else {
                boolean b = super.onKeyPress(keyCode, scanCode, modifiers);
                int displayedLines = Math.min(Math.max(1, getLines().size() + 1), 5);
                setDisplayedLines(displayedLines);
                height = 7 + displayedLines * client.font.lineHeight;
                position.setRelativeY(-9 - (displayedLines * client.font.lineHeight));
                return b;
            }
        }

        boolean b = super.onKeyPress(keyCode, scanCode, modifiers);
        int displayedLines = Math.min(Math.max(1, getLines().size()), 5);
        setDisplayedLines(displayedLines);
        height = 7 + displayedLines * client.font.lineHeight;
        position.setRelativeY(-9 - (displayedLines * client.font.lineHeight));
        return b;
    }
}
