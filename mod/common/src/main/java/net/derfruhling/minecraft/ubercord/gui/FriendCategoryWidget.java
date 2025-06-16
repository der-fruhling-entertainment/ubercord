package net.derfruhling.minecraft.ubercord.gui;

import dev.lambdaurora.spruceui.widget.container.SpruceEntryListWidget;
import net.derfruhling.minecraft.ubercord.client.Badge;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class FriendCategoryWidget extends SpruceEntryListWidget.Entry {
    private List<FriendCardWidget> cards = new ArrayList<>();
    private boolean expanded = false;
    private final Component title;

    public FriendCategoryWidget(Component title, boolean expanded) {
        this.title = title;
        this.expanded = expanded;
        this.width = 125;
        this.height = 16;
    }

    public void add(FriendCardWidget card) {
        card.getPosition().setAnchor(this);
        card.getPosition().setRelativeY(16 + cards.size() * 32);
        cards.add(card);

        if(expanded) height += 32;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int x = position.getX();
        int y = position.getY();

        graphics.drawString(client.font, (expanded ? Badge.OPENED_CATEGORY_BADGE : Badge.COLLAPSED_CATEGORY_BADGE).create(), x + 4, y + 4, 0xFFFFFFFF);
        graphics.drawString(client.font, title, x + 16, y + 4, 0xFFAAAAAA);

        if(expanded) {
            for (FriendCardWidget card : cards) {
                card.renderWidget(graphics, mouseX, mouseY, delta);
            }
        }
    }

    @Override
    protected boolean onMouseClick(double mouseX, double mouseY, int button) {
        if(mouseY < position.getY() + 16) {
            expanded = !expanded;

            if(expanded) {
                height = 16 + cards.size() * 32;
            } else {
                height = 16;
            }
        } else {
            for (FriendCardWidget card : cards) {
                if (card.mouseClicked(mouseX, mouseY, button)) {
                    break;
                }
            }
        }

        return true;
    }
}
