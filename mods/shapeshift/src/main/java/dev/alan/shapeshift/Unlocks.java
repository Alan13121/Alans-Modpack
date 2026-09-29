package dev.alan.shapeshift;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Immutable list of entity type IDs a player has unlocked, in unlock order. */
public record Unlocks(List<String> ids) {
    public static final Unlocks EMPTY = new Unlocks(List.of());
    public static final Codec<Unlocks> CODEC = Codec.STRING.listOf().xmap(Unlocks::new, Unlocks::ids);
    public static final StreamCodec<ByteBuf, Unlocks> STREAM_CODEC =
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(Unlocks::new, Unlocks::ids);
    public Unlocks {
        ids = List.copyOf(ids);
    }
    public boolean contains(String id) { return ids.contains(id); }
    /** Returns this instance unchanged when {@code id} is already unlocked. */
    public Unlocks with(String id) {
        if (id.isBlank()) throw new IllegalArgumentException("Blank entity id");
        if (contains(id)) return this;
        var next = new ArrayList<>(ids);
        next.add(id);
        return new Unlocks(next);
    }
    public Unlocks withAll(Iterable<String> more) {
        Unlocks result = this;
        for (String id : more) result = result.with(id);
        return result;
    }
}
