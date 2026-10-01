package dev.alan.combat;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Levels an armor piece has received from each ore, stored on the item as the {@code combat:upgrades} component. */
public record Upgrades(Map<String, Integer> levels) {
    public static final Upgrades EMPTY = new Upgrades(Map.of());
    public static final Codec<Upgrades> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(Upgrades::new, Upgrades::levels);
    public static final StreamCodec<io.netty.buffer.ByteBuf, Upgrades> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public Upgrades {
        levels = Map.copyOf(levels);
    }
    public int level(String ore) { return levels.getOrDefault(ore, 0); }
    public int total() { return levels.values().stream().mapToInt(Integer::intValue).sum(); }
    public Upgrades with(String ore, int level) {
        var next = new HashMap<>(levels);
        next.put(ore, level);
        return new Upgrades(next);
    }
}
