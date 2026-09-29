package dev.alan.lookup.client;

import dev.alan.lookup.BrewingSync;
import dev.alan.lookup.LookupMod;
import dev.alan.lookup.api.RecipeView;
import dev.alan.lookup.RecipeSync;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.PotionIngredient;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import net.minecraft.world.level.Level;

/**
 * Client-side lookup of recipes by the items they produce and consume. Built from the displays the server
 * sent, so it covers every mod's recipes without knowing about them; unknown display types fall back to
 * their crafting requirements.
 */
public final class RecipeIndex {
    private static final List<RecipeDisplayEntry> received = new ArrayList<>();
    private static final Map<Item, List<RecipeView>> byOutput = new HashMap<>();
    private static final Map<Item, List<RecipeView>> byInput = new HashMap<>();
    private static final List<BrewingSync.Entry> brewing = new ArrayList<>();
    /** Views from plugins, kept apart so they can be rebuilt often without redoing the recipe work. */
    private static final Map<Item, List<RecipeView>> pluginOutput = new HashMap<>();
    private static final Map<Item, List<RecipeView>> pluginInput = new HashMap<>();
    private static final List<Supplier<List<RecipeView>>> providers = new ArrayList<>();
    private static boolean dirty = true;

    private RecipeIndex() {}

    static void receive(RecipeSync sync) {
        if (sync.reset()) received.clear();
        received.addAll(sync.entries());
        dirty = true;
    }

    static void receiveBrewing(BrewingSync sync) {
        brewing.clear();
        brewing.addAll(sync.entries());
        dirty = true;
    }

    static void clear() {
        received.clear();
        brewing.clear();
        byOutput.clear();
        byInput.clear();
        dirty = true;
    }

    public static List<RecipeView> producing(Item item) { return merged(byOutput, pluginOutput, item); }
    public static List<RecipeView> using(Item item) { return merged(byInput, pluginInput, item); }

    static void addProvider(Supplier<List<RecipeView>> provider) { providers.add(provider); }

    /** Re-runs every plugin provider; they may read data that only arrived after startup. */
    /** For game tests, which run outside any screen. */
    public static void refreshPluginsForTest() { refreshPlugins(); }

    static void refreshPlugins() {
        pluginOutput.clear();
        pluginInput.clear();
        for (var provider : providers) {
            List<RecipeView> views;
            try {
                views = provider.get();
            } catch (RuntimeException e) {
                LookupMod.LOG.warn("A lookup plugin failed to provide views", e);
                continue;
            }
            for (RecipeView view : views) index(view, pluginOutput, pluginInput);
        }
    }

    private static List<RecipeView> merged(Map<Item, List<RecipeView>> core, Map<Item, List<RecipeView>> plugin, Item item) {
        List<RecipeView> own = get(core, item), extra = plugin.getOrDefault(item, List.of());
        if (extra.isEmpty()) return own;
        List<RecipeView> all = new ArrayList<>(own);
        all.addAll(extra);
        return all;
    }
    public static int size() { return received.size(); }

    private static List<RecipeView> get(Map<Item, List<RecipeView>> map, Item item) {
        rebuildIfNeeded();
        return map.getOrDefault(item, List.of());
    }

    private static void rebuildIfNeeded() {
        Level level = Minecraft.getInstance().level;
        if (!dirty || level == null) return;
        dirty = false;
        byOutput.clear();
        byInput.clear();
        long started = System.nanoTime();
        ContextMap context = SlotDisplayContext.fromLevel(level);
        for (RecipeDisplayEntry entry : received) {
            RecipeView view;
            try {
                view = view(entry, context);
            } catch (RuntimeException e) {
                continue;
            }
            index(view, byOutput, byInput);
        }
        for (BrewingSync.Entry entry : brewing) {
            try {
                index(brewingView(entry, level), byOutput, byInput);
            } catch (RuntimeException e) {
                // A malformed modded recipe must not hide the rest.
            }
        }
        LookupMod.LOG.info("Lookup indexed {} recipes and {} brewing recipes in {} ms", received.size(), brewing.size(),
            (System.nanoTime() - started) / 1_000_000);
    }

    private static void index(RecipeView view, Map<Item, List<RecipeView>> outputs, Map<Item, List<RecipeView>> inputs) {
        for (var slot : view.outputs()) for (ItemStack stack : slot.stacks()) add(outputs, stack.getItem(), view);
        for (var slot : view.inputs()) for (ItemStack stack : slot.stacks()) add(inputs, stack.getItem(), view);
    }

    private static RecipeView brewingView(BrewingSync.Entry entry, Level level) {
        var stand = new ItemStack(Items.BREWING_STAND);
        return new RecipeView("brewing", stand.getHoverName(), stand,
            List.of(new RecipeView.Slot(0, 18, expand(entry.input(), level)), new RecipeView.Slot(24, 18, expand(entry.reagent(), level))),
            List.of(new RecipeView.Slot(94, 18, List.of(entry.output().create()))), RecipeView.Decoration.BREWING, null);
    }

    /** Potion ingredients match on the potion inside, so list each concrete potion item that passes the test. */
    private static List<ItemStack> expand(PotionIngredient ingredient, Level level) {
        List<ItemStack> out = new ArrayList<>();
        var potions = level.registryAccess().lookupOrThrow(Registries.POTION);
        ingredient.ingredient().items().forEach(holder -> {
            ItemStack plain = new ItemStack(holder.value());
            if (ingredient.test(plain)) out.add(plain);
            if (ingredient.potions().isPresent())
                potions.listElements().forEach((Holder<Potion> potion) -> {
                    ItemStack variant = PotionContents.createItemStack(holder.value(), potion);
                    if (out.size() < 24 && ingredient.test(variant)) out.add(variant);
                });
        });
        if (out.isEmpty()) ingredient.ingredient().items().forEach(h -> out.add(new ItemStack(h.value())));
        return out;
    }

    private static void add(Map<Item, List<RecipeView>> map, Item item, RecipeView view) {
        List<RecipeView> list = map.computeIfAbsent(item, i -> new ArrayList<>());
        if (list.isEmpty() || list.get(list.size() - 1) != view) list.add(view);
    }

    private static RecipeView view(RecipeDisplayEntry entry, ContextMap ctx) {
        var display = entry.display();
        ItemStack station = display.craftingStation().resolveForFirstStack(ctx);
        List<RecipeView.Slot> in = new ArrayList<>();
        var arrow = RecipeView.Decoration.ARROW;
        Component note = null;
        String group;
        switch (display) {
            case ShapedCraftingRecipeDisplay shaped -> {
                group = "crafting";
                for (int i = 0; i < shaped.ingredients().size(); i++)
                    slot(in, shaped.ingredients().get(i), (i % shaped.width()) * 18, (i / shaped.width()) * 18, ctx);
            }
            case ShapelessCraftingRecipeDisplay shapeless -> {
                group = "crafting";
                for (int i = 0; i < shapeless.ingredients().size() && i < 9; i++)
                    slot(in, shapeless.ingredients().get(i), (i % 3) * 18, (i / 3) * 18, ctx);
            }
            case FurnaceRecipeDisplay furnace -> {
                group = "furnace";
                arrow = RecipeView.Decoration.FURNACE;
                slot(in, furnace.ingredient(), 0, 0, ctx);
                slot(in, furnace.fuel(), 0, 36, ctx);
                note = Component.translatable("lookup.smelting.time", number(furnace.duration() / 20f))
                    .append(Component.literal(" · "))
                    .append(Component.translatable("lookup.smelting.xp", number(furnace.experience())));
            }
            case StonecutterRecipeDisplay cutter -> {
                group = "stonecutter";
                slot(in, cutter.input(), 0, 18, ctx);
            }
            case SmithingRecipeDisplay smithing -> {
                group = "smithing";
                slot(in, smithing.template(), 0, 18, ctx);
                slot(in, smithing.base(), 22, 18, ctx);
                slot(in, smithing.addition(), 44, 18, ctx);
            }
            default -> {
                // A mod's own display type: lay its plain ingredient list out in a grid.
                group = display.type().getClass().getName();
                var requirements = entry.craftingRequirements().orElse(List.of());
                for (int i = 0; i < requirements.size() && i < 9; i++) {
                    List<ItemStack> stacks = requirements.get(i).items().map(h -> new ItemStack(h.value())).toList();
                    if (!stacks.isEmpty()) in.add(new RecipeView.Slot((i % 3) * 18, (i / 3) * 18, stacks));
                }
            }
        }
        List<ItemStack> results = display.result().resolveForStacks(ctx);
        var out = List.of(new RecipeView.Slot(94, 18, results));
        String stationId = station.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(station.getItem()).toString();
        Component name = station.isEmpty() ? Component.literal(group) : station.getHoverName();
        return new RecipeView(group + ":" + stationId, name, station.isEmpty() ? results.stream().findFirst().orElse(ItemStack.EMPTY) : station,
            in, out, arrow, note, null, entry.id().index());
    }

    private static String number(float value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    private static void slot(List<RecipeView.Slot> slots, SlotDisplay display, int x, int y, ContextMap ctx) {
        List<ItemStack> stacks = display.resolveForStacks(ctx);
        if (!stacks.isEmpty()) slots.add(new RecipeView.Slot(x, y, stacks));
    }
}
