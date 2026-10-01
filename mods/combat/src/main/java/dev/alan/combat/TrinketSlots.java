package dev.alan.combat;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What a player has in the trinket slots, as item ids ("" = empty). Trinkets carry no item data, so ids are enough. */
public record TrinketSlots(List<String> ids) {
    public static final TrinketSlots EMPTY = new TrinketSlots(List.of());
    public static final Codec<TrinketSlots> CODEC = Codec.STRING.listOf().xmap(TrinketSlots::new, TrinketSlots::ids);
    public static final StreamCodec<ByteBuf, TrinketSlots> STREAM_CODEC =
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(TrinketSlots::new, TrinketSlots::ids);
    public TrinketSlots {
        ids = List.copyOf(ids);
    }
    public String at(int index) { return index >= 0 && index < ids.size() ? ids.get(index) : ""; }
    public TrinketSlots with(int index, String id) {
        var next = new ArrayList<>(ids);
        while (next.size() <= index) next.add("");
        next.set(index, id);
        return new TrinketSlots(next);
    }
}
