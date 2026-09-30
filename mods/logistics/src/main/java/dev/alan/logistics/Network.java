package dev.alan.logistics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/**
 * One connected warehouse: every {@link NetworkNode} reachable through face-adjacent nodes, plus the vanilla
 * containers touching any of them and the storage cells among them.
 *
 * <p>Networks are cached: {@link #scan} only walks the cables again after a block next to the network changed (see
 * {@link #invalidate}) or a chunk it touches was loaded or unloaded. Inside a network, per-container counts and an
 * item index are kept up to date incrementally: a container is only read again when its block entity reported a
 * change (through the {@code setChanged} counter added by {@code BlockEntityMixin}), plus a few containers per
 * operation as a safety net against mods that forget to report. So an operation costs time proportional to the
 * containers that actually hold the item, not to the size of the warehouse.
 */
public final class Network {
    public enum Status { OK, NO_CONTROLLER, MULTIPLE_CONTROLLERS }

    /** Hard cap so a runaway cable line can't stall the server. */
    private static final int MAX_NODES = 32768;
    /** A cached network is rebuilt after this many game ticks even if nothing reported a change. */
    private static final long MAX_AGE = 1200;

    /** Hash key that treats stacks as equal when item and components match. */
    public static final class Key {
        private final ItemStack stack;
        private final int hash;

        /** Copies the stack (count 1), so the key can be kept. */
        public Key(ItemStack stack) {
            this.stack = stack.copyWithCount(1);
            this.hash = ItemStack.hashItemAndComponents(this.stack);
        }

        private Key(ItemStack stack, int hash) {
            this.stack = stack;
            this.hash = hash;
        }

        /** A key that borrows the stack, only for looking up; never store it. */
        static Key probe(ItemStack stack) { return new Key(stack, ItemStack.hashItemAndComponents(stack)); }

        public ItemStack stack() { return stack; }
        @Override public boolean equals(Object o) { return o instanceof Key k && k.hash == hash && ItemStack.isSameItemSameComponents(stack, k.stack); }
        @Override public int hashCode() { return hash; }
    }

    /** Per item type: the total and the sources that hold any. */
    private static final class Entry {
        final Key key;
        long total;
        final LinkedHashSet<Source> holders = new LinkedHashSet<>();
        Entry(Key key) { this.key = key; }
    }

    /** One vanilla container or storage cell with what it held when it was last read. */
    private static final class Source {
        final BlockEntity be;
        final Container container;
        final CellBlockEntity cell;
        int seen = -1;
        int empty;
        Map<Key, Integer> counts = Map.of();

        Source(BlockEntity be) {
            this.be = be;
            this.container = be instanceof Container c ? c : null;
            this.cell = be instanceof CellBlockEntity c ? c : null;
        }
    }

    // ---- cache ------------------------------------------------------------------------------------------------

    private static final class LevelCache {
        final Map<BlockPos, Network> byNode = new HashMap<>();
        final Set<Network> networks = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static final Map<Level, LevelCache> CACHE = new IdentityHashMap<>();

    /** Finds the network the node at {@code start} belongs to (cached). */
    public static Network scan(Level level, BlockPos start) {
        if (level.isClientSide()) return build(level, start.immutable());
        LevelCache cache = CACHE.computeIfAbsent(level, l -> new LevelCache());
        Network cached = cache.byNode.get(start);
        if (cached != null) {
            if (level.getGameTime() - cached.builtAt <= MAX_AGE) return cached;
            cached.drop(cache);
        }
        Network network = build(level, start.immutable());
        cache.networks.add(network);
        for (BlockPos node : network.nodes) cache.byNode.put(node, network);
        return network;
    }

    /** A block at or next to {@code pos} changed: forget every cached network that includes or touches it. */
    public static void invalidate(Level level, BlockPos pos) {
        LevelCache cache = CACHE.get(level);
        if (cache == null) return;
        Network here = cache.byNode.get(pos);
        if (here != null) here.drop(cache);
        for (Direction dir : Direction.values()) {
            Network next = cache.byNode.get(pos.relative(dir));
            if (next != null) next.drop(cache);
        }
    }

    /** A chunk was loaded or unloaded: networks that touch it may have gained or lost blocks. */
    public static void invalidateChunk(Level level, ChunkPos chunk) {
        LevelCache cache = CACHE.get(level);
        if (cache == null) return;
        for (Network n : new ArrayList<>(cache.networks)) if (n.chunks.contains(chunk.pack())) n.drop(cache);
    }

    public static void clearCaches() { CACHE.clear(); }

    private void drop(LevelCache cache) {
        for (BlockPos node : nodes) cache.byNode.remove(node, this);
        cache.networks.remove(this);
    }

    // ---- building ---------------------------------------------------------------------------------------------

    public final Status status;
    private final long builtAt;
    private final List<BlockPos> nodes;
    private final Set<Long> chunks;
    private final List<Source> containers = new ArrayList<>();
    private final List<Source> cells = new ArrayList<>();
    private final Map<Key, Entry> entries = new HashMap<>();
    private long revision;
    private int rolling;

    private Network(Status status, long builtAt, List<BlockPos> nodes, Set<Long> chunks) {
        this.status = status;
        this.builtAt = builtAt;
        this.nodes = nodes;
        this.chunks = chunks;
    }

    private static Network build(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> storagePositions = new HashSet<>();
        Set<Long> chunks = new HashSet<>();
        List<BlockEntity> containerEntities = new ArrayList<>();
        List<BlockEntity> cellEntities = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> nodes = new ArrayList<>();
        int controllers = 0;
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() <= MAX_NODES) {
            BlockPos pos = queue.poll();
            nodes.add(pos);
            chunks.add(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
            if (level.getBlockState(pos).getBlock() instanceof ControllerBlock) controllers++;
            if (level.getBlockEntity(pos) instanceof CellBlockEntity cell) cellEntities.add(cell);
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                chunks.add(ChunkPos.pack(next.getX() >> 4, next.getZ() >> 4));
                if (!level.hasChunkAt(next)) continue;
                if (level.getBlockState(next).getBlock() instanceof NetworkNode) {
                    if (seen.add(next)) queue.add(next);
                } else if (isStorage(level.getBlockEntity(next)) && storagePositions.add(next)) {
                    containerEntities.add(level.getBlockEntity(next));
                }
            }
        }
        Status status = controllers == 0 ? Status.NO_CONTROLLER : controllers > 1 ? Status.MULTIPLE_CONTROLLERS : Status.OK;
        Network network = new Network(status, level.getGameTime(), nodes, chunks);
        for (BlockEntity be : containerEntities) network.containers.add(new Source(be));
        for (BlockEntity be : cellEntities) network.cells.add(new Source(be));
        return network;
    }

    /** Only plain storage blocks are adopted, so hoppers, furnaces and dispensers are never fed by accident. */
    public static boolean isStorage(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
    }

    public boolean usable() { return status == Status.OK; }

    // ---- keeping the counts up to date ------------------------------------------------------------------------

    private static int modCount(BlockEntity be) { return ((ModCounted) (Object) be).logistics$modCount(); }

    private void refresh() {
        for (Source s : containers) if (s.seen != modCount(s.be) || s.be.isRemoved()) reload(s);
        for (Source s : cells) if (s.seen != modCount(s.be) || s.be.isRemoved()) reload(s);
        // Safety net: re-read a few sources every time, in case something changed a container without reporting it.
        int total = containers.size() + cells.size();
        for (int i = 0, n = Math.max(1, Math.min(8, total / 512)); i < n && total > 0; i++) {
            rolling = (rolling + 1) % total;
            reload(rolling < containers.size() ? containers.get(rolling) : cells.get(rolling - containers.size()));
        }
    }

    private void reload(Source source) {
        Map<Key, Integer> next = new HashMap<>();
        int[] empty = {0};
        if (source.be.isRemoved()) {
            // Nothing readable; treated as empty until the topology cache drops this network.
        } else if (source.container != null) {
            Container c = source.container;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack stack = c.getItem(i);
                if (stack.isEmpty()) empty[0]++; else count(next, stack, stack.getCount());
            }
        } else {
            source.cell.forEach((stack, n) -> count(next, stack, n));
        }
        boolean changed = false;
        for (var old : source.counts.entrySet()) {
            Entry entry = entries.get(old.getKey());
            Integer now = next.get(old.getKey());
            if (now == null) {
                entry.total -= old.getValue();
                entry.holders.remove(source);
                if (entry.holders.isEmpty()) entries.remove(entry.key);
                changed = true;
            } else if (!now.equals(old.getValue())) {
                entry.total += now - old.getValue();
                changed = true;
            }
        }
        for (var added : next.entrySet()) {
            if (source.counts.containsKey(added.getKey())) continue;
            Entry entry = entries.get(added.getKey());
            entry.total += added.getValue();
            entry.holders.add(source);
            changed = true;
        }
        source.counts = next;
        source.empty = empty[0];
        source.seen = modCount(source.be);
        if (changed) revision++;
    }

    /** Adds to a per-source count, creating the network-wide entry (and its canonical key) the first time an item is seen. */
    private void count(Map<Key, Integer> into, ItemStack stack, int amount) {
        Entry entry = entries.get(Key.probe(stack));
        if (entry == null) {
            entry = new Entry(new Key(stack));
            entries.put(entry.key, entry);
        }
        into.merge(entry.key, amount, Integer::sum);
    }

    // ---- reading ----------------------------------------------------------------------------------------------

    /** Changes whenever any total changes; lets callers skip work when nothing did. */
    public long revision() {
        refresh();
        return revision;
    }

    /** How many of exactly this item (with its components) the warehouse holds. */
    public long count(ItemStack template) {
        refresh();
        Entry entry = entries.get(Key.probe(template));
        return entry == null ? 0 : entry.total;
    }

    public int typeCount() {
        refresh();
        return entries.size();
    }

    public void forEach(BiConsumer<Key, Long> action) {
        refresh();
        for (Entry e : entries.values()) action.accept(e.key, e.total);
    }

    /** Every distinct item in the warehouse with its total count (a copy). */
    public Map<Key, Long> contents() {
        Map<Key, Long> map = new HashMap<>();
        forEach(map::put);
        return map;
    }

    // ---- moving items -----------------------------------------------------------------------------------------

    /** Takes up to {@code amount} of {@code template} out of the warehouse. */
    public ItemStack extract(ItemStack template, int amount) {
        refresh();
        ItemStack out = template.copyWithCount(0);
        Entry entry = entries.get(Key.probe(template));
        if (entry == null) return out;
        for (Source source : entry.holders) {
            if (out.getCount() >= amount) break;
            if (source.container == null) {
                out.grow(source.cell.extract(template, amount - out.getCount()));
                continue;
            }
            Container c = source.container;
            for (int i = 0; i < c.getContainerSize() && out.getCount() < amount; i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, template)) continue;
                int moved = Math.min(s.getCount(), amount - out.getCount());
                out.grow(moved);
                s.shrink(moved);
                if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY);
                c.setChanged();
            }
        }
        return out;
    }

    /**
     * Stores {@code stack} and returns what did not fit. Items go where the same item already lives first,
     * then into empty chest slots, and only then start a new entry in a cell.
     */
    public ItemStack insert(ItemStack stack) {
        refresh();
        ItemStack rest = stack.copy();
        Entry entry = entries.get(Key.probe(stack));
        if (entry != null) {
            for (Source source : entry.holders) {
                if (rest.isEmpty()) break;
                if (source.container != null) mergeInto(source.container, rest); else source.cell.insert(rest, false);
            }
        }
        for (Source source : containers) {
            if (rest.isEmpty()) break;
            if (source.empty > 0) fillEmpty(source.container, rest);
        }
        for (Source source : cells) if (!rest.isEmpty()) source.cell.insert(rest, true);
        return rest;
    }

    private static void mergeInto(Container c, ItemStack rest) {
        for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, rest)) continue;
            int room = Math.min(s.getMaxStackSize(), c.getMaxStackSize(s)) - s.getCount();
            int moved = Math.min(room, rest.getCount());
            if (moved > 0) { s.grow(moved); rest.shrink(moved); c.setChanged(); }
        }
    }

    private static void fillEmpty(Container c, ItemStack rest) {
        for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
            if (!c.getItem(i).isEmpty() || !c.canPlaceItem(i, rest)) continue;
            int moved = Math.min(rest.getCount(), Math.min(rest.getMaxStackSize(), c.getMaxStackSize(rest)));
            if (moved > 0) { c.setItem(i, rest.copyWithCount(moved)); rest.shrink(moved); c.setChanged(); }
        }
    }
}
