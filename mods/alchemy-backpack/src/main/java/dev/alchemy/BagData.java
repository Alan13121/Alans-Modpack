package dev.alchemy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.ArrayList;

/** Immutable, persistent contents carried by each individual backpack. */
public record BagData(long energy, List<String> learned) {
    public static final BagData EMPTY = new BagData(0, List.of());
    public static final Codec<BagData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.LONG.validate(value -> value >= 0 ? com.mojang.serialization.DataResult.success(value) : com.mojang.serialization.DataResult.error(() -> "Negative energy")).fieldOf("energy").forGetter(BagData::energy),
        Codec.STRING.listOf().fieldOf("learned").forGetter(BagData::learned)
    ).apply(i, BagData::new));
    public BagData {
        if (energy < 0) throw new IllegalArgumentException("Negative energy");
        learned = List.copyOf(learned);
    }
    public BagData deposit(String id, long value, int count) {
        if (value <= 0 || count <= 0) throw new IllegalArgumentException("Invalid deposit");
        long total = Math.addExact(energy, Math.multiplyExact(value, (long) count));
        var next = new ArrayList<>(learned);
        if (!next.contains(id)) next.add(id);
        return new BagData(total, next);
    }
    public BagData withdraw(long value, int count) {
        if (value <= 0 || count <= 0) throw new IllegalArgumentException("Invalid exchange");
        long cost = Math.multiplyExact(value, (long) count);
        if (cost > energy) throw new IllegalArgumentException("Insufficient energy");
        return new BagData(energy - cost, learned);
    }
}
