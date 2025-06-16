package net.derfruhling.minecraft.ubercord.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.widget.container.SpruceEntryListWidget;

import java.util.List;

public class ChatMessageListWidget extends SpruceEntryListWidget<ChatMessage> {
    public ChatMessageListWidget(Position position, int width, int height, int anchorYOffset, Class<ChatMessage> entryClass) {
        super(position, width, height, anchorYOffset, entryClass);
    }

    public void addMessage(ChatMessage message) {
        addEntry(message);
        setScrollAmount(getMaxScroll());
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }
}
