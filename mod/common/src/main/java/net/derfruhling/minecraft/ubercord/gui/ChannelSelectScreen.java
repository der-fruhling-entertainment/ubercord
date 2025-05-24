package net.derfruhling.minecraft.ubercord.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.screen.SpruceScreen;
import dev.lambdaurora.spruceui.util.ColorUtil;
import dev.lambdaurora.spruceui.widget.SpruceButtonWidget;
import dev.lambdaurora.spruceui.widget.SpruceLabelWidget;
import dev.lambdaurora.spruceui.widget.container.SpruceEntryListWidget;
import net.derfruhling.discord.socialsdk4j.GuildChannel;
import net.derfruhling.discord.socialsdk4j.Lobby;
import net.derfruhling.minecraft.ubercord.client.JoinedChannel;
import net.derfruhling.minecraft.ubercord.client.UbercordClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ChannelSelectScreen extends SpruceScreen implements DiesOnChannelChange {
    private SpruceEntryListWidget<Option> list = null;
    private SpruceButtonWidget selectButton;
    private int selectedIndex = -1;
    private long channelId = -1;
    private final GuildSelectScreen parent;
    private final long guildId;

    public class Option extends SpruceEntryListWidget.Entry {
        private final long channelId;
        private final String name;
        private final ResourceLocation textureName;
        private final int index;

        public Option(long channelId, String name, int index) {
            this.channelId = channelId;
            this.name = name;
            textureName = ResourceLocation.fromNamespaceAndPath("ubercord", "textures/gui/textchannel.png");
            this.index = index;
            width = ChannelSelectScreen.this.width;
            height = 24;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            int x = position.getX();
            int y = position.getY();

            RenderSystem.enableBlend();
            if(ChannelSelectScreen.this.selectedIndex == index) {
                graphics.fill(RenderType.gui(), position.getX(), position.getY(), x + width - 8, y + height, 0x55aaaaaa);
                graphics.renderOutline(position.getX(), position.getY(), width - 8, height, ColorUtil.WHITE);
            }
            graphics.blit(textureName, x + 4, y + 4, 0, 16, 16, 16, 16, 16, 16);
            RenderSystem.disableBlend();

            graphics.drawString(Minecraft.getInstance().font, name, x + 16 + 8, y + 8, 0xffffffff);
        }

        @Override
        protected boolean onMouseClick(double mouseX, double mouseY, int button) {
            ChannelSelectScreen.this.selectedIndex = index;
            ChannelSelectScreen.this.channelId = channelId;
            ChannelSelectScreen.this.selectButton.setActive(true);
            return true;
        }
    }

    public ChannelSelectScreen(GuildSelectScreen parent, long guildId) {
        super(Component.literal("miaw"));
        this.parent = parent;
        this.guildId = guildId;
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(new SpruceLabelWidget(Position.of(4, 4), Component.literal("Select the guild you want to link to:"), width));

        if(list == null) {
            list = new OptionListWidget(guildId);
        }

        addRenderableWidget(list);

        addRenderableWidget(new SpruceButtonWidget(Position.of(4, height - 24), 60, 20, Component.literal("Back"), (button) -> {
            onClose();
        }));

        selectButton = new SpruceButtonWidget(Position.of(width - 64, height - 24), 60, 20, Component.literal("Select >>"), (button) -> {
            JoinedChannel lobby = Objects.requireNonNull(UbercordClient.get().getCurrentChannel());

            UbercordClient.get().getClient().linkChannelToLobby(lobby.lobbyId(), channelId, result -> {
                if(Minecraft.getInstance().player == null) return;

                if(result.isSuccess()) {
                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("Linked channel successfully"));
                } else {
                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("Failed to link channel: " + result.message()).withStyle(ChatFormatting.RED));
                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("Ensure you have the required permissions to link this lobby. Ubercord servers provide /party-admin for this purpose."));
                }
            });

            Minecraft.getInstance().setScreen(null);
        });
        selectButton.setActive(selectedIndex >= 0);
        addRenderableWidget(selectButton);
    }

    private class OptionListWidget extends SpruceEntryListWidget<Option> {
        public OptionListWidget(long guildId) {
            super(Position.of(4, 20), ChannelSelectScreen.this.width - 8, ChannelSelectScreen.this.height - 48, 0, Option.class);

            UbercordClient.get().getClient().getGuildChannels(guildId, (result, guilds) -> {
                if(result.isSuccess()) {
                    int i = 0;
                    List<Option> options = new ArrayList<>();

                    for (GuildChannel ch : guilds) {
                        if(ch.isLinkable()) {
                            options.add(new Option(ch.id(), ch.name(), i++));
                        }
                    }

                    replaceEntries(options);
                } else {
                    throw new RuntimeException("failed to fetch channel list for guild " + guildId + ": " + result.message());
                }
            });
        }
    }
}
