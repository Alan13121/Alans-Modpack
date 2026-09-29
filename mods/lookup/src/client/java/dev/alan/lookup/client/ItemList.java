package dev.alan.lookup.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Every item that exists, in creative-menu order, with the search filter used by the side list. */
public final class ItemList {
    private static List<ItemStack> cache = List.of();
    /** Tooltip text per listed stack, built on first {@code $} search. */
    private static final Map<ItemStack, String> tooltips = new IdentityHashMap<>();

    private ItemList() {}

    static void clear() {
        cache = List.of();
        tooltips.clear();
    }

    public static List<ItemStack> all(Level level) {
        if (!cache.isEmpty()) return cache;
        Map<Item, ItemStack> ordered = new LinkedHashMap<>();
        try {
            var params = new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess());
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
                tab.buildContents(params);
                for (ItemStack stack : tab.getDisplayItems()) ordered.putIfAbsent(stack.getItem(), stack.copy());
            }
        } catch (RuntimeException e) {
            // Creative tabs are only a nicer order; fall through to the registry.
        }
        for (Item item : BuiltInRegistries.ITEM)
            if (item != net.minecraft.world.item.Items.AIR && item.isEnabled(level.enabledFeatures()))
                ordered.putIfAbsent(item, new ItemStack(item));
        return cache = List.copyOf(ordered.values());
    }

    private static String tooltip(ItemStack stack) {
        return tooltips.computeIfAbsent(stack, s -> {
            var mc = Minecraft.getInstance();
            var sb = new StringBuilder();
            for (var line : s.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL))
                sb.append(line.getString().toLowerCase(Locale.ROOT)).append('\n');
            return sb.toString();
        });
    }

    private static boolean hasTag(ItemStack stack, String term) {
        return BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).tags()
            .anyMatch(tag -> tag.location().toString().contains(term));
    }

    /**
     * Space-separated terms, all must match: plain text against the name, {@code @mod} against the namespace,
     * {@code #tag} against the item's tags, {@code $text} against its tooltip.
     */
    public static List<ItemStack> filter(List<ItemStack> all, String query) {
        String[] terms = query.toLowerCase(Locale.ROOT).strip().split("\\s+");
        if (terms.length == 1 && terms[0].isEmpty()) return all;
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : all) {
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
            boolean match = true;
            for (String term : terms) {
                if (term.isEmpty()) continue;
                boolean ok = switch (term.charAt(0)) {
                    case '@' -> namespace.contains(term.substring(1));
                    case '#' -> hasTag(stack, term.substring(1));
                    case '$' -> tooltip(stack).contains(term.substring(1));
                    default -> name.contains(term);
                };
                if (!ok) { match = false; break; }
            }
            if (match) out.add(stack);
        }
        return out;
    }
}
