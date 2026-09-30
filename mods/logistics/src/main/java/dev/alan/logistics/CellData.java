package dev.alan.logistics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** What a storage cell holds; also the data component that lets a broken cell keep its items. */
public record CellData(List<Entry> entries) {
    public static final CellData EMPTY = new CellData(List.of());

    /** {@code stack} is a count-1 template; the real amount lives in {@code count}. */
    public record Entry(ItemStack stack, int count) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("item").forGetter(Entry::stack),
            Codec.INT.fieldOf("count").forGetter(Entry::count)).apply(i, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, Entry::stack,
            ByteBufCodecs.VAR_INT, Entry::count,
            Entry::new);
    }

    public static final Codec<CellData> CODEC = Entry.CODEC.listOf().xmap(CellData::new, CellData::entries);
    public static final StreamCodec<RegistryFriendlyByteBuf, CellData> STREAM_CODEC =
        Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(CellData::new, CellData::entries);

    /**
     * Adds up several cells' contents. Returns null if the result would need more than {@code maxTypes} item types
     * or more than {@code maxPerType} of one item, so a compression never destroys items.
     */
    public static CellData merge(List<CellData> parts, int maxTypes, int maxPerType) {
        java.util.Map<Network.Key, Long> sums = new java.util.LinkedHashMap<>();
        for (CellData part : parts)
            for (Entry e : part.entries()) sums.merge(new Network.Key(e.stack()), (long) e.count(), Long::sum);
        if (sums.size() > maxTypes) return null;
        List<Entry> out = new java.util.ArrayList<>();
        for (var e : sums.entrySet()) {
            if (e.getValue() > maxPerType) return null;
            out.add(new Entry(e.getKey().stack(), e.getValue().intValue()));
        }
        return new CellData(out);
    }

    public long total() {
        long sum = 0;
        for (Entry e : entries) sum += e.count();
        return sum;
    }
}
