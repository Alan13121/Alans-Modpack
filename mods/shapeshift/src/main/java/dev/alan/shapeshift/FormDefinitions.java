package dev.alan.shapeshift;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/**
 * Form definitions from data packs. The server loads them on start and /reload, then sends them to
 * every client so both sides agree (climbing is simulated on the client, tooltips show abilities).
 */
public final class FormDefinitions {
    private static final Set<MobCategory> AQUATIC = EnumSet.of(MobCategory.WATER_CREATURE, MobCategory.WATER_AMBIENT,
        MobCategory.UNDERGROUND_WATER_CREATURE, MobCategory.AXOLOTLS);
    private static Map<String, FormDefinition> server = Map.of();
    private static Map<String, FormDefinition> client = Map.of();
    private static final Map<EntityType<?>, Resolved> serverCache = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, Resolved> clientCache = new ConcurrentHashMap<>();
    private FormDefinitions() {}

    public static Map<String, FormDefinition> serverDefinitions() { return server; }
    public static void setClient(Map<String, FormDefinition> definitions) {
        client = Map.copyOf(definitions);
        clientCache.clear();
    }

    public static FormDefinition get(EntityType<?> type, boolean isClient) {
        return (isClient ? client : server).getOrDefault(Forms.id(type), FormDefinition.EMPTY);
    }

    /** Everything a form grants, combining the entity's own stats, automatic traits and its data file. */
    public record Resolved(Set<Ability> abilities, float flyingSpeed, Map<String, ModifierSpec> modifiers, double maxHealth,
                           Optional<ActiveAbility> active, Set<Weakness> weaknesses, List<String> scares, List<String> huntedBy) {
        public boolean has(Ability ability) { return abilities.contains(ability); }
        public boolean has(Weakness weakness) { return weaknesses.contains(weakness); }
    }

    /** Cached per side; cleared whenever definitions change. Resolving runs every tick for shapeshifted players. */
    public static Resolved resolve(EntityType<?> type, boolean isClient) {
        return (isClient ? clientCache : serverCache).computeIfAbsent(type, t -> compute(t, isClient));
    }

    private static Resolved compute(EntityType<?> type, boolean isClient) {
        FormDefinition def = get(type, isClient);
        var abilities = EnumSet.noneOf(Ability.class);
        abilities.addAll(def.abilities());
        if (type.fireImmune()) abilities.add(Ability.FIRE_IMMUNE);
        if (AQUATIC.contains(type.getCategory())) {
            abilities.add(Ability.WATER_BREATHING);
            abilities.add(Ability.SWIM_FAST);
        }
        var mobBase = baseValues(type);
        var dims = Forms.dimensions(type);
        var modifiers = FormPlan.modifiers(mobBase, baseValues(EntityTypes.PLAYER), def, abilities, dims.width(), dims.height());
        var weaknesses = EnumSet.noneOf(Weakness.class);
        weaknesses.addAll(def.weaknesses());
        if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(EntityTypeTags.BURN_IN_DAYLIGHT)) weaknesses.add(Weakness.BURNS_IN_DAYLIGHT);
        return new Resolved(Set.copyOf(abilities), def.flyingSpeed().orElse(0.05f), modifiers, FormPlan.maxHealth(mobBase, def),
            def.active(), Set.copyOf(weaknesses), def.scares(), def.huntedBy());
    }

    /** Whether {@code type} is named by one of the selectors ("minecraft:wolf" or "#minecraft:skeletons"). */
    public static boolean matches(List<String> selectors, EntityType<?> type) {
        if (selectors.isEmpty()) return false;
        var holder = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type);
        String id = Forms.id(type);
        for (String selector : selectors) {
            if (selector.startsWith("#")) {
                var tagId = Identifier.tryParse(selector.substring(1));
                if (tagId != null && holder.is(TagKey.create(Registries.ENTITY_TYPE, tagId))) return true;
            } else if (selector.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Whether any loaded form lists {@code type} under "scares" / "hunted_by". Used to decide which mobs get extra AI goals. */
    public static boolean anyScares(EntityType<?> type) {
        return server.values().stream().anyMatch(def -> matches(def.scares(), type));
    }
    public static boolean anyHuntedBy(EntityType<?> type) {
        return server.values().stream().anyMatch(def -> matches(def.huntedBy(), type));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Double> baseValues(EntityType<?> type) {
        AttributeSupplier supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
        var values = new HashMap<String, Double>();
        for (String id : FormPlan.INHERITED)
            BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(id)).ifPresent(holder -> {
                if (supplier.hasAttribute(holder)) values.put(id, supplier.getBaseValue(holder));
            });
        return values;
    }

    /** Reads {@code data/<namespace>/shapeshift/forms/<path>.json} as the form for entity {@code <namespace>:<path>}. */
    public static final class Loader extends SimpleJsonResourceReloadListener<FormDefinition> {
        public Loader() { super(FormDefinition.CODEC, FileToIdConverter.json("shapeshift/forms")); }
        @Override protected void apply(Map<Identifier, FormDefinition> loaded, ResourceManager manager, ProfilerFiller profiler) {
            var next = new HashMap<String, FormDefinition>();
            loaded.forEach((id, def) -> {
                if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) next.put(id.toString(), def);
                else ShapeshiftMod.LOG.warn("Form file for unknown entity {} ignored", id);
            });
            server = Map.copyOf(next);
            serverCache.clear();
            ShapeshiftMod.LOG.info("Loaded {} shapeshift form definitions", server.size());
        }
    }
}
