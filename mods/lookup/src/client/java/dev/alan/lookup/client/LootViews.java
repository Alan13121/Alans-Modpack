package dev.alan.lookup.client;

import dev.alan.lookup.LootSync;
import dev.alan.lookup.api.RecipeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;

/** Turns the loot summary the server sent into views: what a mob, a block or a loot table can drop. */
final class LootViews {
    /** 3 columns of 3 rows fit beside the source slot on the canvas. */
    private static final int COLS = 3, PER_VIEW = COLS * 3;
    private static final List<LootSync.Source> received = new ArrayList<>();

    private LootViews() {}

    static void receive(LootSync sync) {
        if (sync.reset()) received.clear();
        received.addAll(sync.sources());
    }

    static void clear() { received.clear(); }

    static List<RecipeView> views() {
        List<RecipeView> out = new ArrayList<>();
        for (LootSync.Source source : received) {
            ItemStack input = input(source);
            if (input.isEmpty()) continue;
            String key = "loot:" + source.kind().name().toLowerCase(Locale.ROOT);
            Component name = Component.translatable("lookup.loot." + source.kind().name().toLowerCase(Locale.ROOT));
            ItemStack icon = new ItemStack(switch (source.kind()) {
                case MOB -> Items.BONE;
                case BLOCK -> Items.IRON_PICKAXE;
                case TABLE -> Items.CHEST;
            });
            for (int from = 0; from < source.drops().size(); from += PER_VIEW) {
                List<RecipeView.Slot> outputs = new ArrayList<>();
                var chunk = source.drops().subList(from, Math.min(source.drops().size(), from + PER_VIEW));
                for (int i = 0; i < chunk.size(); i++) {
                    LootSync.Drop drop = chunk.get(i);
                    Item item = BuiltInRegistries.ITEM.getValue(drop.item());
                    outputs.add(new RecipeView.Slot(46 + (i % COLS) * 18, (i / COLS) * 18,
                        List.of(new ItemStack(item, Math.max(1, Math.min(drop.max(), item.getDefaultMaxStackSize())))), extra(drop)));
                }
                out.add(new RecipeView(key, name, icon, List.of(new RecipeView.Slot(0, 18, List.of(input))), outputs,
                    RecipeView.Decoration.SHORT_ARROW, null));
            }
        }
        return out;
    }

    private static List<Component> extra(LootSync.Drop drop) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("lookup.loot.amount",
            drop.min() == drop.max() ? String.valueOf(drop.max()) : drop.min() + "-" + drop.max()).withStyle(net.minecraft.ChatFormatting.GRAY));
        if (drop.chance() < 0.999f)
            lines.add(Component.translatable("lookup.loot.chance", percent(drop.chance())).withStyle(net.minecraft.ChatFormatting.GRAY));
        return lines;
    }

    private static String percent(float chance) {
        float p = chance * 100f;
        return p >= 10 || p == Math.floor(p) ? String.valueOf(Math.round(p)) : String.format(Locale.ROOT, "%.1f", p);
    }

    /** The slot that stands for the source: a spawn egg, the block, or a named chest. */
    private static ItemStack input(LootSync.Source source) {
        switch (source.kind()) {
            case MOB -> {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(source.id());
                var egg = SpawnEggItem.byId(type);
                if (egg.isPresent()) return new ItemStack(egg.get());
                ItemStack stack = new ItemStack(Items.BONE);
                stack.set(DataComponents.CUSTOM_NAME, type.getDescription());
                return stack;
            }
            case BLOCK -> {
                Block block = BuiltInRegistries.BLOCK.getValue(source.id());
                return new ItemStack(block.asItem());
            }
            default -> {
                ItemStack stack = new ItemStack(Items.CHEST);
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(source.id().getPath()));
                return stack;
            }
        }
    }
}
