package dev.alan.logistics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Saved with the world: where every teleporter is and which channel it was last on (so the destination list also
 * shows pads in chunks that are not loaded), and where the chunk loaders are (so chunks they force can be released
 * when a loader disappears without telling us).
 */
public final class TransmissionData extends SavedData {
    private record Pad(GlobalPos pos, int channel) {
        static final Codec<Pad> CODEC = RecordCodecBuilder.create(i -> i.group(
            GlobalPos.CODEC.fieldOf("pos").forGetter(Pad::pos),
            Codec.INT.fieldOf("channel").forGetter(Pad::channel)
        ).apply(i, Pad::new));
    }

    private static final Codec<TransmissionData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Pad.CODEC.listOf().optionalFieldOf("pads", List.of()).forGetter(d -> {
            List<Pad> list = new ArrayList<>();
            d.pads.forEach((pos, channel) -> list.add(new Pad(pos, channel)));
            return list;
        }),
        GlobalPos.CODEC.listOf().optionalFieldOf("loaders", List.of()).forGetter(d -> new ArrayList<>(d.loaders))
    ).apply(i, TransmissionData::new));

    public static final SavedDataType<TransmissionData> TYPE = new SavedDataType<>(
        LogisticsMod.id("transmission"), TransmissionData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<GlobalPos, Integer> pads = new LinkedHashMap<>();
    private final Set<GlobalPos> loaders = new LinkedHashSet<>();

    public TransmissionData() { this(List.of(), List.of()); }

    private TransmissionData(List<Pad> pads, List<GlobalPos> loaders) {
        for (Pad pad : pads) this.pads.put(pad.pos(), pad.channel());
        this.loaders.addAll(loaders);
    }

    public static TransmissionData get(MinecraftServer server) { return server.getDataStorage().computeIfAbsent(TYPE); }

    // ---- teleporters ------------------------------------------------------------------------------------------

    public void setPad(GlobalPos pos, int channel) {
        Integer old = pads.put(pos, channel);
        if (old == null || old != channel) setDirty();
    }

    public void removePad(GlobalPos pos) {
        if (pads.remove(pos) != null) setDirty();
    }

    /** Every known pad on the channel (loaded or not). */
    public List<GlobalPos> padsOn(int channel) {
        List<GlobalPos> out = new ArrayList<>();
        pads.forEach((pos, ch) -> { if (ch == channel) out.add(pos); });
        return out;
    }

    // ---- chunk loaders ----------------------------------------------------------------------------------------

    public boolean addLoader(GlobalPos pos) {
        boolean added = loaders.add(pos);
        if (added) setDirty();
        return added;
    }

    public void removeLoader(GlobalPos pos) {
        if (loaders.remove(pos)) setDirty();
    }

    public boolean hasLoader(GlobalPos pos) { return loaders.contains(pos); }

    public List<GlobalPos> loaders() { return new ArrayList<>(loaders); }
}
