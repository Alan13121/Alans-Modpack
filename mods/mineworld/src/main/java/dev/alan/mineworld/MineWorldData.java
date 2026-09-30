package dev.alan.mineworld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/** Every mine world (id, seed, where its cauldron is) and the ore progress of cauldrons that are still filling up. */
public final class MineWorldData extends SavedData {
    public record WorldInfo(long seed, Optional<GlobalPos> origin, boolean roomBuilt) {
        static final Codec<WorldInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("seed").forGetter(WorldInfo::seed),
            GlobalPos.CODEC.optionalFieldOf("origin").forGetter(WorldInfo::origin),
            Codec.BOOL.optionalFieldOf("room_built", false).forGetter(WorldInfo::roomBuilt)
        ).apply(i, WorldInfo::new));
    }

    private record Progress(GlobalPos pos, int mask) {
        static final Codec<Progress> CODEC = RecordCodecBuilder.create(i -> i.group(
            GlobalPos.CODEC.fieldOf("pos").forGetter(Progress::pos),
            Codec.INT.fieldOf("mask").forGetter(Progress::mask)
        ).apply(i, Progress::new));
    }

    private static final Codec<MineWorldData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("next_id", 1).forGetter(d -> d.nextId),
        Codec.unboundedMap(Codec.STRING, WorldInfo.CODEC).optionalFieldOf("worlds", Map.of()).forGetter(d -> d.worlds),
        Progress.CODEC.listOf().optionalFieldOf("progress", List.of()).forGetter(d -> {
            List<Progress> list = new ArrayList<>();
            d.progress.forEach((pos, mask) -> list.add(new Progress(pos, mask)));
            return list;
        })
    ).apply(i, MineWorldData::new));

    public static final SavedDataType<MineWorldData> TYPE = new SavedDataType<>(
        MineWorldMod.id("worlds"), MineWorldData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private int nextId;
    private final Map<String, WorldInfo> worlds = new LinkedHashMap<>();
    private final Map<GlobalPos, Integer> progress = new HashMap<>();

    public MineWorldData() { this(1, Map.of(), List.of()); }

    private MineWorldData(int nextId, Map<String, WorldInfo> worlds, List<Progress> progress) {
        this.nextId = nextId;
        this.worlds.putAll(worlds);
        for (Progress p : progress) this.progress.put(p.pos(), p.mask());
    }

    public static MineWorldData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Registers a brand new world with its own seed and returns its id ("w1", "w2", ...). */
    public String createWorld() {
        String id = "w" + nextId++;
        worlds.put(id, new WorldInfo(new java.util.Random().nextLong(), Optional.empty(), false));
        setDirty();
        return id;
    }

    public @Nullable WorldInfo world(String id) { return worlds.get(id); }
    public java.util.Set<String> worldIds() { return java.util.Set.copyOf(worlds.keySet()); }

    public void setOrigin(String id, GlobalPos origin) {
        WorldInfo w = worlds.get(id);
        if (w != null) { worlds.put(id, new WorldInfo(w.seed(), Optional.of(origin), w.roomBuilt())); setDirty(); }
    }

    public void setRoomBuilt(String id) {
        WorldInfo w = worlds.get(id);
        if (w != null) { worlds.put(id, new WorldInfo(w.seed(), w.origin(), true)); setDirty(); }
    }

    public int progress(GlobalPos pos) { return progress.getOrDefault(pos, 0); }

    public void setProgress(GlobalPos pos, int mask) {
        if (mask == 0) progress.remove(pos); else progress.put(pos, mask);
        setDirty();
    }
}
