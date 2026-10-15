#!/usr/bin/env python3
"""Generates the worldgen JSON and block textures of the mineworld mod (no libraries needed).
Ore counts are vanilla per-chunk vein counts times ORE_MULTIPLIER, spread over the whole world height.
Cave chambers come from the noise router; each chamber belongs to one of the CAVE_BIOMES ecosystems.
Textures are only written when missing; use --force to regenerate them."""
import gzip, io, json, os, random, shutil, struct, sys, zlib

ORE_MULTIPLIER = 8
ROOT = os.path.join(os.path.dirname(__file__), "..", "mods", "mineworld", "src", "main", "resources")
DATA = os.path.join(ROOT, "data", "mineworld")
ASSETS = os.path.join(ROOT, "assets", "mineworld")

# (name, vanilla configured feature, vanilla veins per chunk)
ORES = [
    ("coal", "minecraft:ore_coal", 30), ("coal_buried", "minecraft:ore_coal_buried", 20),
    ("iron", "minecraft:ore_iron", 20), ("iron_small", "minecraft:ore_iron_small", 10),
    ("copper", "minecraft:ore_copper_small", 16), ("copper_large", "minecraft:ore_copper_large", 4),
    ("gold", "minecraft:ore_gold_buried", 5),
    ("redstone", "minecraft:ore_redstone", 12),
    ("lapis", "minecraft:ore_lapis", 2), ("lapis_buried", "minecraft:ore_lapis_buried", 4),
    ("diamond", "minecraft:ore_diamond_small", 7), ("diamond_medium", "minecraft:ore_diamond_medium", 2),
    ("diamond_buried", "minecraft:ore_diamond_buried", 4), ("diamond_large", "minecraft:ore_diamond_large", 1),
    ("emerald", "minecraft:ore_emerald", 10),
]
# Stone variety, generated before the ores (not multiplied).
BLOBS = [("granite", "minecraft:ore_granite", 20), ("diorite", "minecraft:ore_diorite", 20),
         ("andesite", "minecraft:ore_andesite", 20), ("tuff", "minecraft:ore_tuff", 10)]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


HEIGHT = {"type": "minecraft:uniform", "min_inclusive": {"absolute": -64}, "max_inclusive": {"absolute": 319}}

# Sparse cave chambers: air where the cave_cheese noise (std ~1/3) rises above CAVE_THRESHOLD.
CAVE_THRESHOLD = 0.28
CAVE_XZ_SCALE, CAVE_Y_SCALE = 2.0, 3.2
# Solid sphere around the spawn room so no chamber cuts into it.
SPAWN_POINT, SPAWN_RADIUS = [0, 129, 0], 20

# Cave ecosystems, picked by two climate noises: (temperature, humidity) of each biome.
CAVE_BIOMES = {"crystal_cave": (-0.3, -0.3), "lush_cave": (-0.3, 0.3),
               "dripstone_cave": (0.3, -0.3), "ore_nest": (0.3, 0.3)}

MONSTERS = [("spider", 100, 4), ("zombie", 95, 4), ("zombie_villager", 5, 1), ("skeleton", 100, 4),
            ("creeper", 100, 4), ("enderman", 10, 2), ("witch", 5, 1)]


def placed(feature, count):
    return {"feature": feature, "placement": [
        {"type": "minecraft:count", "count": count},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": HEIGHT},
        {"type": "minecraft:biome"}]}


def scanned(feature, count, direction, target, offset):
    """Like vanilla lush/dripstone placements: scan up or down from a random air block to a surface."""
    return {"feature": feature, "placement": [
        {"type": "minecraft:count", "count": count},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": HEIGHT},
        {"type": "minecraft:environment_scan", "direction_of_search": direction, "target_condition": target,
         "allowed_search_condition": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}, "max_steps": 12},
        {"type": "minecraft:offset", "x": 0, "y": offset, "z": 0},
        {"type": "minecraft:biome"}]}


SOLID = {"type": "minecraft:solid"}
STURDY_BELOW = {"type": "minecraft:has_sturdy_face", "direction": "down"}

# Extra features in generation order: (step, name, placed feature, biomes). Every biome must list shared
# features in the same order, so one global list is filtered per biome.
EXTRA = [
    (2, "amethyst_geode", placed("minecraft:amethyst_geode", 1), ["crystal_cave"]),
    (2, "large_dripstone", placed("minecraft:large_dripstone", 30), ["dripstone_cave"]),
    (3, "monster_room", placed("minecraft:monster_room", 8), ["ore_nest"]),
    (6, "ore_clay", placed("minecraft:ore_clay", 46), ["lush_cave"]),
    (7, "dripstone_cluster", placed("minecraft:dripstone_cluster", 72), ["dripstone_cave"]),
    (7, "pointed_dripstone", placed("minecraft:pointed_dripstone", 224), ["dripstone_cave"]),
    (9, "glow_lichen", placed("minecraft:glow_lichen", 200), ["crystal_cave"]),
    (9, "moss_ceiling", scanned("minecraft:moss_patch_ceiling", 250, "up", SOLID, -1), ["lush_cave"]),
    (9, "cave_vines", scanned("minecraft:cave_vine", 376, "up", STURDY_BELOW, -1), ["lush_cave"]),
    (9, "lush_clay", scanned("minecraft:lush_caves_clay", 124, "down", SOLID, 1), ["lush_cave"]),
    (9, "moss_floor", scanned("minecraft:moss_patch", 250, "down", SOLID, 1), ["lush_cave"]),
    (9, "rooted_azalea", scanned("minecraft:rooted_azalea_tree", 3, "up", SOLID, -1), ["lush_cave"]),
    (9, "spore_blossom", scanned("minecraft:spore_blossom", 50, "up", SOLID, -1), ["lush_cave"]),
]


def spawns(**categories):
    cats = {c: [] for c in ["ambient", "axolotls", "creature", "misc", "monster",
                            "underground_water_creature", "water_ambient", "water_creature"]}
    for cat, entries in categories.items():
        cats[cat] = [{"type": f"minecraft:{t}", "count": n, "weight": w} for t, w, n in entries]
    return {"argument": {"spawn_costs": {}, "spawns_by_category": cats}, "modifier": "overlay"}


BIOME_SPAWNS = {
    "crystal_cave": spawns(ambient=[("bat", 10, 8)], monster=MONSTERS),
    "lush_cave": spawns(ambient=[("bat", 10, 8)], axolotls=[("axolotl", 10, 4)], monster=MONSTERS),
    "dripstone_cave": spawns(ambient=[("bat", 10, 8)], monster=MONSTERS + [("cave_spider", 60, 2)]),
    "ore_nest": spawns(ambient=[("bat", 10, 8)], monster=MONSTERS + [("cave_spider", 30, 2), ("witch", 20, 1)]),
}


def biome(features, spawn_attr):
    steps = [[] for _ in range(11)]
    for step, name in features:
        steps[step].append(f"mineworld:{name}")
    return {"attributes": {"minecraft:gameplay/natural_mob_spawns": spawn_attr,
                           "minecraft:visual/sky_color": "#000000"},
            "carvers": [], "downfall": 0.0, "effects": {"water_color": "#3f76e4"},
            "features": steps, "has_precipitation": False, "temperature": 0.5}


def noise(name, xz, y):
    return {"type": "minecraft:noise", "noise": name, "xz_scale": xz, "y_scale": y}


def worldgen():
    ores = []
    for name, feature, count in BLOBS:
        write(f"{DATA}/worldgen/placed_feature/{name}.json", placed(feature, count))
        ores.append((6, name))
    for name, feature, count in ORES:
        write(f"{DATA}/worldgen/placed_feature/ore_{name}.json", placed(feature, count * ORE_MULTIPLIER))
        ores.append((6, f"ore_{name}"))
    # Ore nests get every ore a second time.
    rich = []
    for name, feature, count in ORES:
        write(f"{DATA}/worldgen/placed_feature/ore_rich_{name}.json", placed(feature, count * ORE_MULTIPLIER))
        rich.append((6, f"ore_rich_{name}"))
    for _, name, feature, _ in EXTRA:
        write(f"{DATA}/worldgen/placed_feature/{name}.json", feature)

    # Old worlds keep their already generated chunks in the single "mine" biome.
    write(f"{DATA}/worldgen/biome/mine.json", biome(ores, spawns()))
    for b in CAVE_BIOMES:
        features = ores + (rich if b == "ore_nest" else []) + [(s, n) for s, n, _, bs in EXTRA if b in bs]
        features.sort(key=lambda f: f[0])  # stable: keeps the global order inside each step
        write(f"{DATA}/worldgen/biome/{b}.json", biome(features, BIOME_SPAWNS[b]))

    for n in ("cave_kind_a", "cave_kind_b"):
        write(f"{DATA}/worldgen/noise/{n}.json", {"base_octave": -7, "octave_count": 2})
    caves = {"type": "minecraft:interpolated", "cell_size_xz": 4, "cell_size_y": 4, "input": {
        "type": "minecraft:sub", "left": CAVE_THRESHOLD,
        "right": noise("minecraft:cave_cheese", CAVE_XZ_SCALE, CAVE_Y_SCALE)}}
    spawn_shield = {"type": "minecraft:sub", "left": 1.0, "right": {"type": "minecraft:mul",
        "left": {"type": "minecraft:distance_to_point", "point": SPAWN_POINT, "metric": "euclidean"},
        "right": 1.0 / SPAWN_RADIUS}}
    # Solid bands at the bottom and top so chambers never break through the bedrock floor and roof.
    floor = {"type": "minecraft:gradient", "axis": "y", "from_coordinate": -64, "to_coordinate": -56, "from_value": 1.0, "to_value": -1.0}
    roof = {"type": "minecraft:gradient", "axis": "y", "from_coordinate": 311, "to_coordinate": 319, "from_value": -1.0, "to_value": 1.0}
    density = caves
    for part in (spawn_shield, floor, roof):
        density = {"type": "minecraft:max", "left": density, "right": part}
    zero = 0.0
    write(f"{DATA}/worldgen/noise_settings/mine.json", {
        "default_block": "minecraft:stone", "default_fluid": "minecraft:water",
        "disable_mob_generation": True, "legacy_random_source": False,
        "material_rule": {"type": "minecraft:sequence", "sequence": [
            "minecraft:bedrock_floor", "minecraft:bedrock_roof",
            {"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": ["mineworld:ore_nest"]},
             "then_run": {"type": "minecraft:block", "result_state": "minecraft:deepslate"}},
            {"type": "minecraft:condition", "if_true": {"type": "minecraft:vertical_gradient",
                "false_at_and_above": {"absolute": 8}, "random_name": "minecraft:deepslate",
                "true_at_and_below": {"absolute": 0}},
             "then_run": {"type": "minecraft:block", "result_state": "minecraft:deepslate"}}]},
        "noise": {"height": 384, "min_y": -64},
        "noise_router": {"chunk_surface_level": zero, "continents": zero, "depth": zero, "erosion": zero,
                         "final_density": density,
                         "ridges": zero,
                         "temperature": noise("mineworld:cave_kind_a", 1.0, 1.0),
                         "vegetation": noise("mineworld:cave_kind_b", 1.0, 1.0)},
        "sea_level": -64, "spawn_target": []})
    # Vanilla mineshafts (placed near the world bottom because the sea level is the bottom).
    write(f"{ROOT}/data/minecraft/tags/worldgen/biome/has_structure/mineshaft.json",
          {"replace": False, "values": [f"mineworld:{b}" for b in CAVE_BIOMES]})
    city_worldgen()
    write(f"{DATA}/dimension_type/mine.json", {
        "ambient_light": 0.0,
        "attributes": {
            "minecraft:audio/ambient_sounds": {"mood": {"block_search_extent": 8, "offset": 2.0,
                "sound": "minecraft:ambient.cave", "tick_delay": 6000}},
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": True},
            "minecraft:gameplay/straw_bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": True},
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/respawn_anchor_works": False,
            "minecraft:visual/ambient_light_color": "#000000",
            "minecraft:visual/fog_color": "#000000",
            "minecraft:visual/sky_color": "#000000",
            "minecraft:visual/fog_start_distance": 0.0,
            "minecraft:visual/fog_end_distance": 64.0,
            "minecraft:visual/sky_light_factor": 0.0},
        "coordinate_scale": 1.0, "has_ceiling": False, "has_ender_dragon_fight": False, "has_fixed_time": True,
        "has_skylight": False, "height": 384, "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 384, "min_y": -64, "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": 0, "skybox": "none"})


# ---------------------------------------------------------------- monster cities

DATA_VERSION = 5023  # Minecraft 26.3 (SharedConstants.WORLD_VERSION); templates without it get "upgraded" from 1.12.
CITY_SPACING, CITY_SEPARATION, CITY_SALT = 44, 30, 26101501


def city_worldgen():
    overrides = {"monster": {"bounding_box": "full", "spawns": []}}
    for name, first in (("monster_city_first", True), ("monster_city", False)):
        write(f"{DATA}/worldgen/structure/{name}.json", {
            "type": "mineworld:monster_city", "biomes": "#mineworld:has_monster_city",
            "spawn_overrides": overrides, "step": "top_layer_modification", "first": first})
    write(f"{DATA}/worldgen/structure_set/monster_city_first.json", {
        "structures": [{"structure": "mineworld:monster_city_first", "weight": 1}],
        "placement": {"type": "mineworld:fixed_chunk", "chunk_x": 0, "chunk_z": 9}})
    write(f"{DATA}/worldgen/structure_set/monster_cities.json", {
        "structures": [{"structure": "mineworld:monster_city", "weight": 1}],
        "placement": {"type": "minecraft:random_spread", "spacing": CITY_SPACING,
                      "separation": CITY_SEPARATION, "salt": CITY_SALT}})
    write(f"{DATA}/tags/worldgen/biome/has_monster_city.json", {"values": [f"mineworld:{b}" for b in CAVE_BIOMES]})
    write(f"{DATA}/tags/worldgen/structure/monster_city.json",
          {"values": ["mineworld:monster_city_first", "mineworld:monster_city"]})


# --- minimal NBT writer (big endian, gzip) for structure templates

def _nbt_payload(tag, value):
    t, v = value
    if t == "byte": return struct.pack(">b", v)
    if t == "int": return struct.pack(">i", v)
    if t == "string":
        b = v.encode("utf-8")
        return struct.pack(">H", len(b)) + b
    if t == "list":
        inner, items = v
        ids = {"byte": 1, "int": 3, "string": 8, "list": 9, "compound": 10}
        return struct.pack(">bi", ids[inner] if items else 0, len(items)) + b"".join(_nbt_payload(None, (inner, i)) for i in items)
    if t == "compound":
        return b"".join(_nbt_named(k, x) for k, x in v.items()) + b"\x00"
    raise ValueError(t)


def _nbt_named(name, value):
    ids = {"byte": 1, "int": 3, "string": 8, "list": 9, "compound": 10}
    b = name.encode("utf-8")
    return struct.pack(">bH", ids[value[0]], len(b)) + b + _nbt_payload(name, value)


def S(v): return ("string", v)
def I(v): return ("int", v)
def C(**kw): return ("compound", kw)


class Template:
    """Collects blocks (x, y, z) -> (name, properties, block entity nbt); y = 0 is the city floor layer."""

    def __init__(self):
        self.blocks = {}

    def set(self, x, y, z, name, nbt=None, **props):
        self.blocks[(x, y, z)] = (name if ":" in name else "minecraft:" + name, props, nbt)

    def box(self, x0, y0, z0, x1, y1, z1, name, **props):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, **props)

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.box(x0, y0, z0, x1, y1, z1, "air")

    def marker(self, x, y, z, what):
        self.set(x, y, z, "structure_block", nbt=C(id=S("minecraft:structure_block"), mode=S("DATA"), metadata=S(what)), mode="data")

    def save(self, path):
        palette, index, blocks = [], {}, []
        for (x, y, z), (name, props, nbt) in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                # 26.3 palette keys are "id"/"properties"; the old "Name"/"Properties" silently load as air.
                entry = {"id": S(name)}
                if props:
                    entry["properties"] = C(**{k: S(str(v).lower()) for k, v in props.items()})
                palette.append(entry)
            block = {"pos": ("list", ("int", [x, y, z])), "state": I(index[key])}
            if nbt: block["nbt"] = nbt
            blocks.append(block)
        xs = [p[0] for p in self.blocks]; ys = [p[1] for p in self.blocks]; zs = [p[2] for p in self.blocks]
        size = [max(xs) + 1, max(ys) + 1, max(zs) + 1]
        root = C(DataVersion=I(DATA_VERSION), size=("list", ("int", size)),
                 palette=("list", ("compound", palette)), blocks=("list", ("compound", blocks)),
                 entities=("list", ("compound", [])))
        raw = _nbt_named("", root)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        buf = io.BytesIO()
        with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as g:
            g.write(raw)
        open(path, "wb").write(buf.getvalue())


def door(t, x, z, facing, wood="oak"):
    t.set(x, 1, z, f"{wood}_door", facing=facing, half="lower", hinge="left", open="false", powered="false")
    t.set(x, 2, z, f"{wood}_door", facing=facing, half="upper", hinge="left", open="false", powered="false")


def bed(t, x, z, facing="south", color="red"):
    dz = {"south": 1, "north": -1}[facing]
    t.set(x, 1, z, f"{color}_bed", facing=facing, part="foot", occupied="false")
    t.set(x, 1, z + dz, f"{color}_bed", facing=facing, part="head", occupied="false")


def shell(t, x0, z0, x1, z1, height, wall, floor, roof, corner=None):
    """Hollow building: floor at y 0, walls y 1..height, flat roof at height + 1 with a rim."""
    t.box(x0, 0, z0, x1, 0, z1, floor)
    for y in range(1, height + 1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or z in (z0, z1)
                if not edge:
                    t.set(x, y, z, "air")
                elif corner and x in (x0, x1) and z in (z0, z1):
                    t.set(x, y, z, corner, axis="y")
                else:
                    t.set(x, y, z, wall)
    t.box(x0, height + 1, z0, x1, height + 1, z1, roof)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                t.set(x, height + 2, z, roof.replace("_planks", "_slab").replace("bricks", "brick_slab"), type="bottom")
    # windows in the middle of each long side
    for x in range(x0 + 2, x1 - 1, 3):
        t.set(x, 2, z0, "glass_pane"); t.set(x, 2, z1, "glass_pane")
    for z in range(z0 + 2, z1 - 1, 3):
        t.set(x0, 2, z, "glass_pane"); t.set(x1, 2, z, "glass_pane")


HOUSES = [  # (width, depth, height, wall, floor, roof, corner log)
    (9, 7, 4, "oak_planks", "spruce_planks", "spruce_planks", "oak_log"),
    (11, 9, 4, "stone_bricks", "oak_planks", "dark_oak_planks", None),
    (7, 7, 5, "spruce_planks", "spruce_planks", "dark_oak_planks", "spruce_log"),
    (11, 7, 4, "mud_bricks", "oak_planks", "oak_planks", "oak_log"),
    (9, 9, 5, "deepslate_bricks", "spruce_planks", "deepslate_bricks", None),
    (13, 9, 4, "birch_planks", "birch_planks", "spruce_planks", "birch_log"),
]


def house(variant):
    w, d, h, wall, floor, roof, corner = HOUSES[variant]
    t = Template()
    t.box(0, 0, 0, 15, 0, 15, "stone_bricks")  # plot ground
    x0, z0 = (16 - w) // 2, (16 - d) // 2 + 1
    x1, z1 = x0 + w - 1, z0 + d - 1
    shell(t, x0, z0, x1, z1, h, wall, floor, roof, corner)
    mid = (x0 + x1) // 2
    door(t, mid, z0, "north", "spruce" if "spruce" in floor else "oak")
    t.box(mid - 1, 0, 0, mid + 1, 0, z0 - 1, "polished_andesite")  # path to the lane
    t.set(x0 + 1, h, z0 + 1, "lantern", hanging="true")
    t.set(x1 - 1, h, z1 - 1, "lantern", hanging="true")
    bed(t, x1 - 1, z1 - 2, "south")
    t.set(x0 + 1, 1, z1 - 1, "barrel", facing="up")
    t.set(x0 + 2, 1, z1 - 1, "crafting_table")
    t.set(x0 - 1 if x0 > 0 else 0, 1, z0 - 1, "lantern")
    t.marker(mid, 1, z0 + 2, "civilian")
    if variant % 2 == 0:
        t.marker(mid + 1, 1, z1 - 2, "civilian")
    return t


def garden():
    t = Template()
    t.box(0, 0, 0, 15, 0, 15, "moss_block")
    t.box(0, 0, 7, 15, 0, 8, "polished_andesite")
    rnd = random.Random(7)
    for x in range(1, 15):
        for z in list(range(1, 6)) + list(range(10, 15)):
            r = rnd.random()
            if r < 0.15: t.set(x, 1, z, "azalea")
            elif r < 0.35: t.set(x, 1, z, "moss_carpet")
            elif r < 0.45: t.set(x, 1, z, rnd.choice(["red_mushroom", "brown_mushroom"]))
    for x in (0, 15):
        for z in (0, 15):
            t.set(x, 1, z, "lantern")
    t.set(7, 1, 7, "lantern")
    t.marker(4, 1, 7, "civilian")
    return t


def plaza():
    t = Template()
    n = 18
    t.box(0, 0, 0, n - 1, 0, n - 1, "polished_andesite")
    t.box(3, 0, 3, n - 4, 0, n - 4, "chiseled_stone_bricks")
    # fountain in the middle (city centre is local 9, 9)
    t.box(7, 0, 7, 11, 1, 11, "stone_bricks")
    t.box(8, 1, 8, 10, 1, 10, "water", level="0")
    t.box(9, 1, 9, 9, 3, 9, "stone_bricks")
    t.set(9, 4, 9, "glowstone")
    t.set(9, 0, 4, "mineworld:return_block")
    for x, z in ((2, 2), (2, 15), (15, 2), (15, 15)):
        t.set(x, 1, z, "stone_brick_wall", up="true")
        t.set(x, 2, z, "lantern")
    # market stalls
    t.box(2, 1, 6, 2, 1, 12, "spruce_slab", type="bottom")
    t.box(15, 1, 6, 15, 1, 12, "spruce_slab", type="bottom")
    t.marker(3, 1, 9, "merchant")
    t.marker(14, 1, 9, "enderman")
    t.marker(6, 1, 13, "creeper")
    t.marker(12, 1, 5, "creeper")
    return t


def inn():
    t = Template()
    shell(t, 0, 0, 35, 15, 5, "spruce_planks", "oak_planks", "dark_oak_planks", "spruce_log")
    door(t, 17, 0, "north", "spruce"); door(t, 18, 0, "north", "spruce")
    t.set(17, 3, 0, "spruce_planks"); t.set(18, 3, 0, "spruce_planks")
    # bar on the west side
    t.box(2, 1, 5, 9, 1, 5, "spruce_planks")
    t.box(2, 2, 5, 9, 2, 5, "spruce_slab", type="bottom")
    t.set(3, 1, 2, "barrel", facing="up"); t.set(4, 1, 2, "barrel", facing="up"); t.set(6, 1, 2, "brewing_stand")
    t.marker(6, 1, 3, "bartender")
    # tables
    for x in (12, 16, 20):
        t.set(x, 1, 8, "oak_fence"); t.set(x, 2, 8, "oak_pressure_plate")
    # bedrooms along the east and south: 8 beds with partitions
    colors = ["red", "blue", "green", "yellow", "white", "cyan", "orange", "purple"]
    for i, x in enumerate(range(23, 35, 3)):
        if i:
            t.box(x - 1, 1, 9, x - 1, 3, 14, "spruce_planks")  # partitions between the south rooms
        bed(t, x, 12, "south", colors[i])
        bed(t, x, 3, "north", colors[i + 4])
    for x in (4, 12, 20, 28, 33):
        t.set(x, 5, 7, "lantern", hanging="true")
    return t


def guard_post():
    t = Template()
    t.box(0, 0, 0, 15, 0, 15, "stone_bricks")
    shell(t, 2, 3, 13, 13, 5, "deepslate_bricks", "polished_deepslate", "deepslate_tiles", None)
    door(t, 7, 3, "north", "dark_oak"); door(t, 8, 3, "north", "dark_oak")
    t.box(7, 0, 0, 8, 0, 2, "polished_andesite")
    for x in (4, 6, 9, 11):
        t.set(x, 1, 12, "barrel", facing="up")
    t.set(4, 1, 5, "smithing_table"); t.set(11, 1, 5, "grindstone", face="floor", facing="north")
    t.set(5, 5, 8, "lantern", hanging="true"); t.set(10, 5, 8, "lantern", hanging="true")
    t.set(6, 1, 0, "lantern"); t.set(9, 1, 0, "lantern")
    t.marker(8, 1, 9, "captain")
    return t


def city_templates():
    out = f"{DATA}/structure/city"
    templates = {"plaza": plaza(), "inn": inn(), "guard_post": guard_post(), "garden": garden()}
    for i in range(len(HOUSES)):
        templates[f"house_{i + 1}"] = house(i)
    for name, t in templates.items():
        path = f"{out}/{name}.nbt"
        # Never overwrite a template that exists (it may have been rebuilt in game); --force regenerates.
        if os.path.exists(path) and "--force" not in sys.argv:
            continue
        t.save(path)


def png(path, px):
    # Never overwrite a texture that already exists (it may have been replaced by hand); pass --force to regenerate.
    if os.path.exists(path) and "--force" not in sys.argv:
        return
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(data)


def canvas(base, seed, noise=10):
    rnd = random.Random(seed)
    return [[[max(0, min(255, base[k] + rnd.randint(-noise, noise))) for k in range(3)] + [255] for _ in range(16)] for _ in range(16)]


def textures():
    tex = f"{ASSETS}/textures/block"
    write(f"{ASSETS}/items/bounty_proof.json", {"model": {"type": "minecraft:model", "model": "mineworld:item/bounty_proof"}})
    write(f"{ASSETS}/models/item/bounty_proof.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "mineworld:item/bounty_proof"}})
    # glowing teal "world water"
    px = canvas((40, 190, 200), 11, 18)
    for y in range(16):
        for x in range(16):
            if (x * 3 + y * 5) % 11 == 0:
                px[y][x] = [180, 255, 250, 255]
    png(f"{tex}/world_water.png", px)
    # unbreakable return block: dark stone with a glowing rune frame
    side = canvas((30, 34, 44), 12, 6)
    for i in range(16):
        for a, b in ((0, i), (15, i), (i, 0), (i, 15)):
            side[a][b] = [16, 18, 24, 255]
    for i in range(3, 13):
        for a, b in ((3, i), (12, i), (i, 3), (i, 12)):
            side[a][b] = [60, 200, 210, 255]
    png(f"{tex}/return_block_side.png", side)
    top = canvas((26, 30, 40), 13, 6)
    for i in range(2, 14):
        for a, b in ((2, i), (13, i), (i, 2), (i, 13)):
            top[a][b] = [60, 200, 210, 255]
    for y in range(6, 10):
        for x in range(6, 10):
            top[y][x] = [190, 255, 250, 255]
    png(f"{tex}/return_block_top.png", top)
    # bounty proof: a parchment slip with a red wax seal (transparent background)
    proof = [[[0, 0, 0, 0] for _ in range(16)] for _ in range(16)]
    rnd = random.Random(14)
    for y in range(2, 14):
        for x in range(3, 13):
            shade = rnd.randint(-12, 8)
            proof[y][x] = [226 + shade, 204 + shade, 150 + shade, 255]
    for x in range(3, 13):
        proof[2][x] = [170, 140, 90, 255]; proof[13][x] = [170, 140, 90, 255]
    for y in (5, 7, 9):
        for x in range(5, 11):
            proof[y][x] = [120, 96, 70, 255]
    for y, x in ((10, 9), (10, 10), (11, 9), (11, 10), (11, 11), (12, 10), (10, 11), (12, 9), (12, 11)):
        proof[y][x] = [180, 30, 30, 255]
    proof[11][10] = [230, 80, 60, 255]
    png(f"{ASSETS}/textures/item/bounty_proof.png", proof)


if __name__ == "__main__":
    shutil.rmtree(f"{DATA}/worldgen", ignore_errors=True)
    worldgen()
    city_templates()
    textures()
    print("done")
