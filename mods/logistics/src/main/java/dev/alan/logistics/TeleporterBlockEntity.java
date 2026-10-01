package dev.alan.logistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Remembers its target and whether it is open. Stepping onto an open pad (when you were not already standing there)
 * costs {@link Energy#TELEPORT_COST} from the channel's energy and moves you to the target pad.
 */
public final class TeleporterBlockEntity extends BlockEntity implements BeaconBeamOwner {
    public static final int CLOSED = 0, ONCE = 1, ALWAYS = 2;
    private static final int BEAM_HEIGHT = 24;
    private static final int BEAM_COLOR = 0xFF7FE8FF;

    private int mode;
    private String targetDim = "";
    private BlockPos target = BlockPos.ZERO;
    private boolean hasTarget;
    /** Players standing on the pad as of the last check; only those who just stepped on are sent. */
    private final Set<UUID> inside = new HashSet<>();

    public TeleporterBlockEntity(BlockPos pos, BlockState state) { super(LogisticsMod.TELEPORTER_ENTITY, pos, state); }

    public int mode() { return mode; }
    public boolean hasTarget() { return hasTarget; }

    public void setTarget(TeleporterDest dest) {
        targetDim = dest.dimension();
        target = dest.pos();
        hasTarget = true;
        setChanged();
    }

    /** Opens or closes the pad; opening needs a target. */
    public boolean setMode(int next) {
        if (next != CLOSED && !hasTarget) return false;
        mode = next;
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            if (state.getBlock() instanceof TeleporterBlock && state.getValue(TeleporterBlock.OPEN) != (next != CLOSED))
                level.setBlock(worldPosition, state.setValue(TeleporterBlock.OPEN, next != CLOSED), 3);
        }
        return true;
    }

    public String dimensionId() { return level == null ? "" : level.dimension().identifier().toString(); }

    /** The channel this pad belongs to (the lowest of its network's), or 0. */
    public int channel() {
        if (level == null) return 0;
        Network network = Network.scan(level, worldPosition);
        return network.usable() && !network.channels().isEmpty() ? network.channels().iterator().next() : 0;
    }

    /** Every other pad on the same channel, nearest first (same dimension before others). */
    public List<TeleporterDest> destinations() {
        int channel = channel();
        List<TeleporterDest> out = new ArrayList<>();
        if (channel == 0) return out;
        List<TeleporterBlockEntity> pads = new ArrayList<>();
        for (TeleporterBlockEntity other : Grid.teleporters())
            if (other != this && !other.isRemoved() && other.getLevel() != null && other.channel() == channel) pads.add(other);
        pads.sort(Comparator.<TeleporterBlockEntity, Boolean>comparing(p -> p.getLevel() != level)
            .thenComparingDouble(p -> p.getLevel() == level ? p.worldPosition.distSqr(worldPosition) : 0)
            .thenComparing(p -> p.dimensionId()).thenComparingLong(p -> p.worldPosition.asLong()));
        for (TeleporterBlockEntity p : pads) out.add(new TeleporterDest(p.dimensionId(), p.worldPosition));
        return out;
    }

    public int selectedIndex(List<TeleporterDest> dests) {
        if (!hasTarget) return -1;
        for (int i = 0; i < dests.size(); i++)
            if (dests.get(i).pos().equals(target) && dests.get(i).dimension().equals(targetDim)) return i;
        return -1;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TeleporterBlockEntity self) {
        if ((level.getGameTime() + pos.hashCode()) % 4 != 0) return;
        AABB zone = new AABB(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1);
        List<ServerPlayer> now = level.getEntitiesOfClass(ServerPlayer.class, zone, p -> !p.isSpectator());
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer p : now) present.add(p.getUUID());
        self.inside.retainAll(present);
        if (self.mode == CLOSED) {
            self.inside.addAll(present);
            return;
        }
        for (ServerPlayer player : now) {
            if (!self.inside.add(player.getUUID())) continue;
            if (self.send(level, player)) {
                self.inside.remove(player.getUUID());
                if (self.mode == ONCE) self.setMode(CLOSED);
                break;
            }
        }
    }

    private boolean send(Level level, ServerPlayer player) {
        if (level.getServer() == null || !hasTarget) return false;
        Identifier id = Identifier.tryParse(targetDim);
        ServerLevel targetLevel = id == null ? null : level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (targetLevel == null || !(targetLevel.getBlockEntity(target) instanceof TeleporterBlockEntity dest)) {
            player.sendOverlayMessage(Component.translatable("logistics.teleporter.missing"));
            return false;
        }
        Network network = Network.scan(level, worldPosition);
        if (!network.usable() || network.channels().isEmpty() || !Energy.spend(level, network.channels(), Energy.TELEPORT_COST)) {
            player.sendOverlayMessage(Component.translatable("logistics.teleporter.no_energy", Energy.TELEPORT_COST));
            return false;
        }
        dest.inside.add(player.getUUID());
        player.teleport(new TeleportTransition(targetLevel, Vec3.atBottomCenterOf(target.above()), Vec3.ZERO,
            player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
        return true;
    }

    @Override public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        if (!level.isClientSide()) Grid.add(this);
    }

    @Override public void clearRemoved() {
        super.clearRemoved();
        if (level != null && !level.isClientSide()) Grid.add(this);
    }

    @Override public void setRemoved() {
        super.setRemoved();
        Grid.remove(this);
    }

    @Override public List<Section> getBeamSections() {
        if (!getBlockState().getValue(TeleporterBlock.OPEN)) return List.of();
        Section section = new Section(BEAM_COLOR);
        for (int i = 1; i < BEAM_HEIGHT; i++) section.increaseHeight();
        return List.of(section);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("mode", mode);
        output.putBoolean("has_target", hasTarget);
        output.putString("target_dim", targetDim);
        output.store("target", BlockPos.CODEC, target);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mode = Math.max(0, Math.min(ALWAYS, input.getIntOr("mode", 0)));
        hasTarget = input.getBooleanOr("has_target", false);
        targetDim = input.getStringOr("target_dim", "");
        target = input.read("target", BlockPos.CODEC).orElse(BlockPos.ZERO);
    }
}
