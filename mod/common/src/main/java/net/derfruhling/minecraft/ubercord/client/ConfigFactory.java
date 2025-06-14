package net.derfruhling.minecraft.ubercord.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.EmptyEntry;
import me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.derfruhling.discord.socialsdk4j.ActivityBuilder;
import net.derfruhling.discord.socialsdk4j.ActivityType;
import net.derfruhling.minecraft.ubercord.DisplayConfig;
import net.derfruhling.minecraft.ubercord.DisplayMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ConfigFactory {
    public static Screen create(@Nullable Screen parent, ClientConfig config) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("ubercord.config.client.title"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        AtomicLong defaultClientId = new AtomicLong();

        ConfigCategory basic = builder.getOrCreateCategory(Component.translatable("ubercord.config.client.category"));
        basic.addEntry(entryBuilder.startLongField(Component.translatable("ubercord.config.client.category.client_id"), config.getDefaultClientId())
                .setDefaultValue(SocialSdkIntegration.BUILTIN_CLIENT_ID)
                .setSaveConsumer(defaultClientId::set)
                .build());

        AtomicReference<DisplayMode> newTitleDisplay = new AtomicReference<>();
        AtomicReference<DisplayConfig> newDisplayConfig = new AtomicReference<>();

        basic.addEntry(addDisplayMode(entryBuilder, false, config.getTitleDisplay(), Component.translatable("ubercord.config.client.title_display"), newTitleDisplay::set));
        basic.addEntry(addDisplayConfig(entryBuilder, true, config.getDefaultConfig(), newDisplayConfig::set));

        basic.addEntry(new EmptyEntry(0) {
            @Override
            public void save() {
                super.save();

                try {
                    ClientConfig config = new ClientConfig(defaultClientId.get(), newTitleDisplay.get(), newDisplayConfig.get());
                    config.save();

                    UbercordClient.get().setConfig(config);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return builder.build();
    }

    public static final String DISABLED_NAME = "Replace this with something to enable this option";

    private static final DisplayMode DEFAULT_MODE = new DisplayMode(
            ActivityType.Playing,
            "",
            "",
            new ActivityBuilder.Assets(
                    new ActivityBuilder.Asset("", ""),
                    new ActivityBuilder.Asset("", "")
            )
    );

    private static SubCategoryListEntry addDisplayMode(ConfigEntryBuilder entryBuilder, boolean isOptional, @Nullable DisplayMode mode, Component name, Consumer<DisplayMode> save) {
        SubCategoryBuilder builder = entryBuilder.startSubCategory(name);

        AtomicBoolean isEnabled = new AtomicBoolean(!isOptional);
        AtomicReference<String> newState = new AtomicReference<>(mode != null ? mode.state() : "");
        AtomicReference<String> newDetails = new AtomicReference<>(mode != null ? mode.details() : "");
        AtomicReference<ActivityType> newType = new AtomicReference<>(mode != null ? mode.type() : ActivityType.Playing);

        if(isOptional) {
            builder.add(entryBuilder.startBooleanToggle(Component.translatable("ubercord.config.toggle_enabled"), isEnabled.get())
                    .setSaveConsumer(isEnabled::set)
                    .build());
        }

        builder.add(entryBuilder.startEnumSelector(Component.translatable("ubercord.config.display_mode.type"), ActivityType.class, newType.get())
                .setSaveConsumer(newType::set)
                .setEnumNameProvider(e -> Component.translatable("ubercord.config.display_mode.type." + e.name().toLowerCase()))
                .build());
        builder.add(entryBuilder.startStrField(Component.translatable("ubercord.config.display_mode.state"), newState.get())
                .setSaveConsumer(newState::set)
                .build());
        builder.add(entryBuilder.startStrField(Component.translatable("ubercord.config.display_mode.details"), newDetails.get())
                .setSaveConsumer(newDetails::set)
                .build());

        SubCategoryBuilder assets = entryBuilder.startSubCategory(Component.translatable("ubercord.config.display_mode.assets"));

        @Nullable ActivityBuilder.Assets modeAssets = mode != null ? mode.assets() : null;
        AtomicReference<String> newLargeImage = new AtomicReference<>(modeAssets != null ? (modeAssets.large() != null ? modeAssets.large().image() : DISABLED_NAME) : DISABLED_NAME);
        AtomicReference<String> newLargeText = new AtomicReference<>(modeAssets != null ? (modeAssets.large() != null ? (modeAssets.large().text() != null ? modeAssets.large().text() : DISABLED_NAME) : DISABLED_NAME) : DISABLED_NAME);
        AtomicReference<String> newSmallImage = new AtomicReference<>(modeAssets != null ? (modeAssets.small() != null ? modeAssets.small().image() : DISABLED_NAME) : DISABLED_NAME);
        AtomicReference<String> newSmallText = new AtomicReference<>(modeAssets != null ? (modeAssets.small() != null ? (modeAssets.small().text() != null ? modeAssets.small().text() : DISABLED_NAME) : DISABLED_NAME) : DISABLED_NAME);

        SubCategoryBuilder large = entryBuilder.startSubCategory(Component.translatable("ubercord.config.display_mode.assets.large"));
        large.add(entryBuilder.startStrField(Component.translatable("ubercord.config.display_mode.assets.large.image"), newLargeImage.get())
                .setSaveConsumer(s -> newLargeImage.set(newLargeImage.get().equals(DISABLED_NAME) ? null : newLargeImage.get()))
                .build());
        large.add(entryBuilder.startStrField(Component.translatable("ubercord.config.display_mode.assets.large.text"), newLargeText.get())
                .setSaveConsumer(s -> newLargeText.set(newLargeText.get().equals(DISABLED_NAME) ? null : newLargeText.get()))
                .build());

        SubCategoryBuilder small = entryBuilder.startSubCategory(Component.translatable("ubercord.config.display_mode.assets.small"));
        small.add(entryBuilder.startStrField(Component.translatable("ubercord.config.display_mode.assets.small.image"), newSmallImage.get())
                .setSaveConsumer(s -> newSmallImage.set(newSmallImage.get().equals(DISABLED_NAME) ? null : newSmallImage.get()))
                .build());
        small.add(entryBuilder.startStrField(Component.translatable("ubercord.config.display_mode.assets.small.text"), newSmallText.get())
                .setSaveConsumer(s -> newSmallText.set(newSmallText.get().equals(DISABLED_NAME) ? null : newSmallText.get()))
                .build());

        assets.add(large.build());
        assets.add(small.build());

        builder.add(assets.build());

        builder.add(new EmptyEntry(0) {
            @Override
            public void save() {
                super.save();
                save.accept(isEnabled.get() ? buildDisplayMode() : null);
            }

            private @NotNull DisplayMode buildDisplayMode() {
                String newLargeImageVal = newLargeImage.get().equals(DISABLED_NAME) ? null : newLargeImage.get();
                String newLargeTextVal = newLargeText.get().equals(DISABLED_NAME) ? null : newLargeText.get();
                String newSmallImageVal = newSmallImage.get().equals(DISABLED_NAME) ? null : newSmallImage.get();
                String newSmallTextVal = newSmallText.get().equals(DISABLED_NAME) ? null : newSmallText.get();

                String newStateVal = newState.get().equals(DISABLED_NAME) ? null : newState.get();
                String newDetailsVal = newDetails.get().equals(DISABLED_NAME) ? null : newDetails.get();

                return new DisplayMode(
                        newType.get(),
                        newStateVal,
                        newDetailsVal,
                        newLargeImageVal != null || newSmallImageVal != null ? new ActivityBuilder.Assets(
                                newLargeImageVal != null ? new ActivityBuilder.Asset(newLargeImageVal, newLargeTextVal) : null,
                                newSmallImageVal != null ? new ActivityBuilder.Asset(newSmallImageVal, newSmallTextVal) : null
                        ) : null
                );
            }
        });

        return builder.build();
    }

    private static SubCategoryListEntry addDisplayConfig(ConfigEntryBuilder entryBuilder, boolean isRoot, DisplayConfig config, Consumer<DisplayConfig> save) {
        SubCategoryBuilder builder = entryBuilder.startSubCategory(Component.translatable("ubercord.config.display_config"));

        AtomicReference<DisplayMode> idle = new AtomicReference<>();
        AtomicReference<DisplayMode> singleplayer = new AtomicReference<>();
        AtomicReference<DisplayMode> multiplayer = new AtomicReference<>();
        AtomicReference<DisplayMode> realms = new AtomicReference<>();
        HashMap<String, DisplayMode> modes = new HashMap<>();
        HashMap<String, String> names = new HashMap<>();

        builder.add(addDisplayMode(entryBuilder, !isRoot, config.idle() != null ? config.idle() : DEFAULT_MODE, Component.translatable("ubercord.config.display_config.idle"), idle::set));
        builder.add(addDisplayMode(entryBuilder, !isRoot, config.playingSingleplayer() != null ? config.playingSingleplayer() : DEFAULT_MODE, Component.translatable("ubercord.config.display_config.singleplayer"), singleplayer::set));
        builder.add(addDisplayMode(entryBuilder, !isRoot, config.playingMultiplayer() != null ? config.playingMultiplayer() : DEFAULT_MODE, Component.translatable("ubercord.config.display_config.multiplayer"), multiplayer::set));
        builder.add(addDisplayMode(entryBuilder, !isRoot, config.playingRealms() != null ? config.playingRealms() : DEFAULT_MODE, Component.translatable("ubercord.config.display_config.realms"), realms::set));

        if (Minecraft.getInstance().level != null) {
            for (ResourceKey<Level> id : Objects.requireNonNull(Minecraft.getInstance().getConnection()).levels()) {
                DisplayMode mode = config.dimensions().get(id.location().toString());
                builder.add(addDisplayMode(entryBuilder, true, mode != null ? mode : DEFAULT_MODE, Component.literal(id.toString()), displayMode -> {
                    modes.put(id.location().toString(), displayMode);
                }));
            }
        }

        builder.add(entryBuilder.startStrList(Component.translatable("ubercord.config.display_config.dimension_names"),
                config.dimensionNames().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList())
                .setCellErrorSupplier(s -> s.contains("=") ? Optional.empty() : Optional.of(Component.translatable("ubercord.config.map_must_contain_equals_error")))
                .setSaveConsumer(strings -> {
                    names.clear();

                    for (String string : strings) {
                        String[] parts = string.split("=", 2);
                        names.put(Objects.requireNonNull(ResourceLocation.tryParse(parts[0])).toString(), parts[1]);
                    }
                })
                .build());

        builder.add(new EmptyEntry(0) {
            @Override
            public void save() {
                super.save();

                save.accept(new DisplayConfig(idle.get(), singleplayer.get(), multiplayer.get(), realms.get(), modes, names));
            }
        });

        return builder.build();
    }

    private static @Nullable DisplayMode orNull(DisplayMode mode) {
        return mode;
    }
}
