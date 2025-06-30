package com.denizenscript.denizenenchantmentfix;

import com.denizenscript.denizen.scripts.containers.core.EnchantmentScriptContainer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.KeyPattern;
import net.kyori.adventure.text.Component;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuppressWarnings({"UnstableApiUsage", "NullableProblems"})
public class EnchantmentFixBootstrap implements PluginBootstrap {

    boolean hadError;
    boolean is1_21;

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        return new DenizenEnchantmentFix(hadError, is1_21);
    }

    @Override
    public void bootstrap(BootstrapContext context) {
        String versionId = ServerBuildInfo.buildInfo().minecraftVersionId();
        String[] splitVersion = StringUtils.split(versionId, '.');
        if (splitVersion.length < 2) {
            context.getLogger().error("Cannot identify version string '{}' (running on snapshot/pre-release version?), enchantment script containers will be disabled.", versionId);
            hadError = true;
            return;
        }
        try {
            is1_21 = Integer.parseInt(splitVersion[1]) >= 21;
        }
        catch (NumberFormatException e) {
            context.getLogger().error("Cannot identify version string '{}' (version is not a number?), enchantment script containers will be disabled.", versionId);
            hadError = true;
            return;
        }
        try {
            final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> nmsComponentsPatchCodec = DataComponentPatch.STREAM_CODEC;
            StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> nmsFilteringCodec = StreamCodec.of((buffer, nmsComponents) -> {
                nmsComponents = filterComponentPatch(nmsComponents, DataComponents.ENCHANTMENTS);
                nmsComponents = filterComponentPatch(nmsComponents, DataComponents.STORED_ENCHANTMENTS);
                nmsComponentsPatchCodec.encode(buffer, nmsComponents);
            }, nmsComponentsPatchCodec);
            ReflectionHelper.getFinalSetter(DataComponentPatch.class, "STREAM_CODEC").invoke(nmsFilteringCodec);
            if (!is1_21) {
                ItemHoverPatch1_20.patch();
            }
            else {
                patchItemHoverCodec1_21();
                List<String> enchantmentIds = YamlConfiguration.loadConfiguration(context.getDataDirectory().resolve("enchant_scripts.yml").toFile()).getStringList("enchantment_ids");
                if (!enchantmentIds.isEmpty()) {
                    context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose(), event -> {
                        for (@KeyPattern.Value String enchantmentId : enchantmentIds) {
                            event.registry().register(RegistryKey.ENCHANTMENT.typedKey(Key.key("denizen", enchantmentId)), builder -> {
                                builder.description(Component.empty())
                                        .supportedItems(RegistrySet.keySet(RegistryKey.ITEM))
                                        .weight(1)
                                        .maxLevel(1)
                                        .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(0, 0))
                                        .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(0, 0))
                                        .anvilCost(0)
                                        .activeSlots(List.of());
                            });
                        }
                    });
                }
            }
        }
        catch (Throwable e) {
            context.getLogger().error("Couldn't initialize Denizen enchantment filtering! enchantment script containers will be disabled.", e);
            hadError = true;
            return;
        }
        if (!is1_21) {
            return;
        }
        final MutableBoolean hadFirstRun = new MutableBoolean(false);
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.ENCHANTMENT), event -> {
            if (hadFirstRun.isFalse()) {
                hadFirstRun.setTrue();
                return;
            }
            List<TypedKey<org.bukkit.enchantments.Enchantment>> treasure = new ArrayList<>(), curse = new ArrayList<>(), tradeable = new ArrayList<>(), discoverable = new ArrayList<>();
            for (EnchantmentScriptContainer.EnchantmentReference enchantmentReference : EnchantmentScriptContainer.registeredEnchantmentContainers.values()) {
                EnchantmentScriptContainer enchantmentScript = enchantmentReference.script;
                // TODO: TypedKey<org.bukkit.enchantments.Enchantment> enchantmentKey = RegistryKey.ENCHANTMENT.typedKey(enchantmentScript.getKey());
                TypedKey<org.bukkit.enchantments.Enchantment> enchantmentKey = RegistryKey.ENCHANTMENT.typedKey(Key.key("denizen", enchantmentScript.id));
                if (enchantmentScript.isTreasureOnly) {
                    treasure.add(enchantmentKey);
                }
                if (enchantmentScript.isCurse) {
                    curse.add(enchantmentKey);
                }
                if (enchantmentScript.isTradable) {
                    tradeable.add(enchantmentKey);
                }
                if (enchantmentScript.isDiscoverable) {
                    discoverable.add(enchantmentKey);
                }
            }
            event.registrar().addToTag(EnchantmentTagKeys.TREASURE, treasure);
            event.registrar().addToTag(EnchantmentTagKeys.CURSE, curse);
            event.registrar().addToTag(EnchantmentTagKeys.TRADEABLE, tradeable);
            event.registrar().addToTag(EnchantmentTagKeys.IN_ENCHANTING_TABLE, discoverable);
        });
    }

    public static void patchItemHoverCodec1_21() {
        final String nmsCodecName = HoverEvent.ShowItem.CODEC.toString();
        MapCodec<HoverEvent.ShowItem> nmsFilteringCodec = MapCodec.of(HoverEvent.ShowItem.CODEC.comap((HoverEvent.ShowItem nmsHover) -> {
            if (!isOnNettyThread()) {
                return nmsHover;
            }
            DataComponentMap nmsOriginalComponents = nmsHover.item().getComponents();
            boolean itemModified = filterItem(nmsHover.item(), DataComponents.ENCHANTMENTS);
            itemModified = filterItem(nmsHover.item(), DataComponents.STORED_ENCHANTMENTS) || itemModified;
            if (!itemModified) {
                return nmsHover;
            }
            ItemStack nmsFilteredItem = nmsHover.item().copy();
            nmsHover.item().applyComponents(nmsOriginalComponents);
            return new HoverEvent.ShowItem(nmsFilteredItem);
        }), HoverEvent.ShowItem.CODEC, () -> nmsCodecName);
        ReflectionHelper.setFieldValue(HoverEvent.Action.class, "codec", HoverEvent.Action.SHOW_ITEM, nmsFilteringCodec);
    }

    public static class ItemHoverPatch1_20 {

        public static final Class<?> ITEM_STACK_INFO_CLASS = ReflectionHelper.getClassOrThrow("net.minecraft.network.chat.HoverEvent$ItemStackInfo");
        public static final MethodHandle ITEM_STACK_INFO_CONSTRUCTOR = ReflectionHelper.getConstructor(ITEM_STACK_INFO_CLASS, Holder.class, int.class, DataComponentPatch.class);
        public static final ReflectionHelper.FieldCache ITEM_STACK_INFO_FIELDS = ReflectionHelper.getFields(ITEM_STACK_INFO_CLASS);
        public static final MethodHandle
                ITEM_STACK_INFO_COMPONENTS = ITEM_STACK_INFO_FIELDS.getGetter("components"),
                ITEM_STACK_INFO_ITEM = ITEM_STACK_INFO_FIELDS.getGetter("item"),
                ITEM_STACK_INFO_COUNT = ITEM_STACK_INFO_FIELDS.getGetter("count");
        public static final Field ITEM_STACK_INFO_CODEC = ITEM_STACK_INFO_FIELDS.get("CODEC");

        public static <T> void patch() throws Throwable {
            Codec<T> nmsHoverCodec = (Codec<T>) ITEM_STACK_INFO_CODEC.get(null);
            Codec<T> nmsFilteringCodec = Codec.of(nmsHoverCodec.comap((T nmsItemStackInfo) -> {
                if (!isOnNettyThread()) {
                    return nmsItemStackInfo;
                }
                try {
                    DataComponentPatch nmsComponentsPatch = (DataComponentPatch) ITEM_STACK_INFO_COMPONENTS.invoke(nmsItemStackInfo);
                    DataComponentPatch nmsModifiedPatch = filterComponentPatch(nmsComponentsPatch, DataComponents.ENCHANTMENTS);
                    nmsModifiedPatch = filterComponentPatch(nmsModifiedPatch, DataComponents.STORED_ENCHANTMENTS);
                    if (nmsModifiedPatch == nmsComponentsPatch) {
                        return nmsItemStackInfo;
                    }
                    return (T) ITEM_STACK_INFO_CONSTRUCTOR.invoke(ITEM_STACK_INFO_ITEM.invoke(nmsItemStackInfo), ITEM_STACK_INFO_COUNT.invoke(nmsItemStackInfo), nmsModifiedPatch);
                }
                catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }), nmsHoverCodec);
            ReflectionHelper.getFinalSetter(ITEM_STACK_INFO_CLASS, "CODEC").invoke(nmsFilteringCodec);
        }
    }

    public static boolean isOnNettyThread() {
        for (Connection connection : MinecraftServer.getServer().getConnection().getConnections()) {
            if (connection.channel.eventLoop().inEventLoop()) {
                return true;
            }
        }
        return false;
    }

    public static boolean filterItem(ItemStack nmsItem, DataComponentType<ItemEnchantments> nmsEnchantmentsComponent) {
        ItemEnchantments nmsEnchantments = nmsItem.get(nmsEnchantmentsComponent);
        if (nmsEnchantments == null) {
            return false;
        }
        ItemEnchantments.Mutable nmsEnchantmentsBuilder = filterEnchantments(nmsEnchantments);
        if (nmsEnchantmentsBuilder == null) {
            return false;
        }
        if (nmsEnchantmentsBuilder.keySet().isEmpty()) {
            nmsItem.remove(nmsEnchantmentsComponent);
            nmsItem.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        else {
            nmsItem.set(nmsEnchantmentsComponent, nmsEnchantmentsBuilder.toImmutable());
        }
        return true;
    }

    public static DataComponentPatch filterComponentPatch(DataComponentPatch nmsComponents, DataComponentType<ItemEnchantments> nmsEnchantmentsComponent) {
        Optional<? extends ItemEnchantments> nmsEnchantmentsOptional = nmsComponents.get(nmsEnchantmentsComponent);
        if (nmsEnchantmentsOptional == null || nmsEnchantmentsOptional.isEmpty()) {
            return nmsComponents;
        }
        ItemEnchantments.Mutable nmsEnchantmentsBuilder = filterEnchantments(nmsEnchantmentsOptional.get());
        if (nmsEnchantmentsBuilder == null) {
            return nmsComponents;
        }
        DataComponentPatch.Builder nmsComponentsBuilder = DataComponentPatch.builder();
        nmsComponentsBuilder.copy(nmsComponents);
        if (nmsEnchantmentsBuilder.keySet().isEmpty()) {
            nmsComponentsBuilder.clear(nmsEnchantmentsComponent);
            nmsComponentsBuilder.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        else {
            nmsComponentsBuilder.set(nmsEnchantmentsComponent, nmsEnchantmentsBuilder.toImmutable());
        }
        return nmsComponentsBuilder.build();
    }

    public static ItemEnchantments.Mutable filterEnchantments(ItemEnchantments nmsEnchantments) {
        if (nmsEnchantments.isEmpty()) {
            return null;
        }
        boolean any = false;
        for (Holder<Enchantment> nmsEnchantment : nmsEnchantments.keySet()) {
            if (isEnchantmentScript(nmsEnchantment)) {
                any = true;
                break;
            }
        }
        if (!any) {
            return null;
        }
        ItemEnchantments.Mutable nmsEnchantmentsBuilder = new ItemEnchantments.Mutable(nmsEnchantments);
        nmsEnchantmentsBuilder.removeIf(EnchantmentFixBootstrap::isEnchantmentScript);
        return nmsEnchantmentsBuilder;
    }

    public static boolean isEnchantmentScript(Holder<Enchantment> nmsEnchantment) {
        ResourceLocation nmsKey = nmsEnchantment.unwrapKey().orElseThrow().location();
        return nmsKey.getNamespace().equals("denizen") && EnchantmentScriptContainer.registeredEnchantmentContainers.containsKey(nmsKey.getPath());
    }
}
