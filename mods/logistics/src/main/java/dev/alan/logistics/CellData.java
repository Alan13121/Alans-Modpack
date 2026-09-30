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

    public long total() {
        long sum = 0;
        for (Entry e : entries) sum += e.count();
        return sum;
    }
}
