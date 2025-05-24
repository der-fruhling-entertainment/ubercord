package net.derfruhling.minecraft.ubercord.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.screen.SpruceScreen;
import dev.lambdaurora.spruceui.util.ColorUtil;
import dev.lambdaurora.spruceui.widget.SpruceButtonWidget;
import dev.lambdaurora.spruceui.widget.SpruceLabelWidget;
import dev.lambdaurora.spruceui.widget.container.SpruceEntryListWidget;
import net.derfruhling.discord.socialsdk4j.GuildMinimal;
import net.derfruhling.minecraft.ubercord.client.UbercordClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class GuildSelectScreen extends SpruceScreen implements DiesOnChannelChange {
    private SpruceEntryListWidget<Option> list = null;
    private SpruceButtonWidget selectButton;
    private int selectedIndex = -1;
    private long guildId = -1;

    public class Option extends SpruceEntryListWidget.Entry {
        private final long guildId;
        private final String name;
        private final int index;

        public Option(long guildId, String name, int index) {
            this.guildId = guildId;
            this.name = name;
            this.index = index;
            width = GuildSelectScreen.this.width;
            height = 40;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int x = position.getX();
            int y = position.getY();

            RenderSystem.enableBlend();
            if(GuildSelectScreen.this.selectedIndex == index) {
                graphics.fill(RenderType.gui(), position.getX(), position.getY(), x + width - 8, y + height, 0x55aaaaaa);
                graphics.renderOutline(position.getX(), position.getY(), width - 8, height, ColorUtil.WHITE);
            }
            RenderSystem.disableBlend();

            graphics.drawString(Minecraft.getInstance().font, name, x + 8, y + 8, 0xffffffff);
            graphics.drawString(Minecraft.getInstance().font, Long.toString(guildId), x + 8, y + 24, 0xffaaaaaa);
        }

        @Override
        protected boolean onMouseClick(double mouseX, double mouseY, int button) {
            GuildSelectScreen.this.selectedIndex = index;
            GuildSelectScreen.this.guildId = guildId;
            GuildSelectScreen.this.selectButton.setActive(true);
            return true;
        }
    }

    public GuildSelectScreen() {
        super(Component.literal("miaw"));
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(new SpruceLabelWidget(Position.of(4, 4), Component.literal("Select the guild you want to link to:"), width));

        if(list == null) {
            list = new OptionListWidget();
        }

        addRenderableWidget(list);

        addRenderableWidget(new SpruceButtonWidget(Position.of(4, height - 24), 60, 20, Component.literal("Back"), (button) -> {
            onClose();
        }));

        selectButton = new SpruceButtonWidget(Position.of(width - 64, height - 24), 60, 20, Component.literal("Select >>"), (button) -> {
            Minecraft.getInstance().setScreen(new ChannelSelectScreen(this, guildId));
        });
        selectButton.setActive(selectedIndex >= 0);
        addRenderableWidget(selectButton);
    }

    private class OptionListWidget extends SpruceEntryListWidget<Option> {
        public OptionListWidget() {
            super(Position.of(4, 20), GuildSelectScreen.this.width - 8, GuildSelectScreen.this.height - 48, 0, Option.class);

            UbercordClient.get().getClient().getUserGuilds((result, guilds) -> {
                if(result.isSuccess()) {
                    int i = 0;
                    List<Option> options = new ArrayList<>();

                    for (GuildMinimal guild : guilds) {
                        options.add(new Option(guild.id(), guild.name(), i++));
                    }

                    replaceEntries(options);
                } else {
                    throw new RuntimeException("failed to fetch guild list for user: " + result.message());
                }
            });
        }
    }
}
