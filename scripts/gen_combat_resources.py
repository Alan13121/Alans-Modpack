#!/usr/bin/env python3
"""Generates the data files and 16x16 item textures of the combat mod (no image libraries needed).
Only writes files that do not exist yet; use --force to regenerate all of them."""
import json, os, struct, sys, zlib

ROOT = os.path.join(os.path.dirname(__file__), "..", "mods", "combat", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "combat")
DATA = os.path.join(ROOT, "data", "combat")
FORCE = "--force" in sys.argv

def write(path, text):
    if os.path.exists(path) and not FORCE:
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w", encoding="utf-8").write(text)

def png(path, px):
    if os.path.exists(path) and not FORCE:
        return
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(data)

def blank():
    return [[[0, 0, 0, 0] for _ in range(16)] for _ in range(16)]

def put(px, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        px[y][x] = list(c) + [255]

def disc(px, cx, cy, r, c, edge):
    for y in range(16):
        for x in range(16):
            d = (x - cx) ** 2 + (y - cy) ** 2
            if d <= r * r:
                put(px, x, y, edge if d > (r - 1.2) ** 2 else c)

def chain(px, cx):
    """Little chain loop above a charm, so the items read as pendants."""
    for y in range(1, 4):
        put(px, cx - 1, y, (150, 150, 160)); put(px, cx + 1, y, (150, 150, 160))
    put(px, cx, 1, (150, 150, 160))

def feather():
    px = blank(); chain(px, 8)
    disc(px, 8, 9, 5, (70, 60, 40), (190, 150, 60))
    for i in range(7):
        put(px, 5 + i // 2 + 1, 12 - i, (245, 245, 250))
        put(px, 6 + i // 2 + 1, 12 - i, (215, 220, 235))
    return px

def night_vision():
    px = blank(); chain(px, 8)
    disc(px, 8, 9, 5, (30, 30, 70), (190, 150, 60))
    for x in range(5, 12):
        put(px, x, 9, (120, 255, 150))
    for x in range(6, 11):
        put(px, x, 8, (80, 200, 110)); put(px, x, 10, (80, 200, 110))
    put(px, 8, 9, (255, 255, 255))
    return px

def gills():
    px = blank(); chain(px, 8)
    disc(px, 8, 9, 5, (30, 70, 90), (190, 150, 60))
    for i in range(3):
        for y in range(6, 12):
            put(px, 5 + i * 2, y, (110, 220, 240))
    return px

def skill():
    px = blank(); chain(px, 8)
    disc(px, 8, 9, 5, (70, 30, 80), (190, 150, 60))
    for i in range(5):
        put(px, 8, 6 + i, (230, 120, 255)); put(px, 6 + i, 9, (230, 120, 255))
    put(px, 8, 9, (255, 255, 255))
    for x, y in ((6, 7), (10, 7), (6, 11), (10, 11)):
        put(px, x, y, (190, 90, 220))
    return px

def fire_ring():
    px = blank(); chain(px, 8)
    disc(px, 8, 9, 5, (90, 25, 15), (190, 150, 60))
    for dy, w, c in ((-3, 1, (255, 200, 60)), (-2, 2, (255, 150, 40)), (-1, 3, (255, 120, 30)), (0, 3, (255, 90, 20)), (1, 2, (230, 60, 10))):
        for x in range(8 - w, 8 + w + 1):
            put(px, x, 9 + dy, c)
    return px

def speed_buckle():
    px = blank(); chain(px, 8)
    disc(px, 8, 9, 5, (60, 55, 20), (190, 150, 60))
    for off in (-2, 1):
        for i in range(3):
            put(px, 6 + off + i, 7 + i, (255, 230, 90))
            put(px, 6 + off + i, 11 - i, (255, 230, 90))
    return px

def spring_insole():
    px = blank(); chain(px, 8)
    disc(px, 8, 9, 5, (30, 70, 30), (190, 150, 60))
    for y in range(6, 13, 2):
        for x in range(6, 11):
            put(px, x, y, (140, 255, 120))
    for y in (7, 11):
        put(px, 6, y, (140, 255, 120)); put(px, 10, y, (140, 255, 120))
    return px

def regen_charm():
    px = blank(); chain(px, 8)
    disc(px, 8, 9, 5, (70, 20, 30), (190, 150, 60))
    for x, y in ((6, 8), (7, 7), (9, 7), (10, 8), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (7, 10), (8, 10), (9, 10), (8, 11), (8, 8)):
        put(px, x, y, (255, 80, 100))
    return px

def magnet():
    px = blank()
    for y in range(3, 13):
        for x in (4, 5, 10, 11):
            put(px, x, y, (210, 40, 40) if y < 7 else (215, 215, 220))
    for x in range(4, 12):
        for y in (12, 13):
            put(px, x, y, (215, 215, 220))
    for x in (4, 5, 10, 11):
        put(px, x, 3, (240, 240, 245))
    return px

def blast_ward():
    px = blank(); chain(px, 8)
    disc(px, 8, 9, 5, (45, 45, 55), (190, 150, 60))
    disc(px, 8, 9, 2, (15, 15, 20), (130, 130, 140))
    for x, y in ((8, 5), (8, 13), (4, 9), (12, 9)):
        put(px, x, y, (200, 200, 210))
    return px

def thorns_ring():
    px = blank(); chain(px, 8)
    disc(px, 8, 9, 4, (30, 85, 35), (190, 150, 60))
    for x, y in ((8, 3), (12, 5), (13, 9), (12, 13), (4, 5), (3, 9), (4, 13), (8, 15)):
        put(px, x, y, (110, 220, 100))
    return px

def hunter_charm():
    px = blank(); chain(px, 8)
    disc(px, 8, 9, 5, (70, 45, 25), (190, 150, 60))
    for i in range(-3, 4):
        put(px, 8 + i, 9, (240, 200, 140)); put(px, 8, 9 + i, (240, 200, 140))
    disc(px, 8, 9, 2, (70, 45, 25), (240, 200, 140))
    return px

def bag():
    px = blank()
    for y in range(5, 14):
        for x in range(3, 13):
            put(px, x, y, (140, 90, 50))
    for x in range(3, 13):
        put(px, x, 5, (95, 60, 35)); put(px, x, 13, (95, 60, 35))
    for y in range(5, 14):
        put(px, 3, y, (95, 60, 35)); put(px, 12, y, (95, 60, 35))
    for x in range(5, 11):
        put(px, x, 3, (200, 200, 205)); put(px, x, 4, (200, 200, 205))
    for y in range(8, 11):
        for x in range(7, 9):
            put(px, x, y, (230, 200, 90))
    return px

ITEMS = {
    "feather_charm": (feather, [" F ", "FGF", " F "], {"F": "minecraft:feather", "G": "minecraft:gold_ingot"}),
    "night_vision_charm": (night_vision, [" G ", "GCG", " G "], {"G": "minecraft:gold_ingot", "C": "minecraft:golden_carrot"}),
    "gills_charm": (gills, [" P ", "PNP", " P "], {"P": "minecraft:prismarine_shard", "N": "minecraft:nautilus_shell"}),
    "skill_charm": (skill, [" G ", "GEG", " G "], {"G": "minecraft:gold_ingot", "E": "minecraft:ender_eye"}),
    "fire_ring": (fire_ring, [" B ", "BMB", " B "], {"B": "minecraft:blaze_powder", "M": "minecraft:magma_cream"}),
    "speed_buckle": (speed_buckle, [" S ", "SGS", " S "], {"S": "minecraft:sugar", "G": "minecraft:gold_ingot"}),
    "spring_insole": (spring_insole, [" S ", "SPS", " S "], {"S": "minecraft:slime_ball", "P": "minecraft:piston"}),
    "regen_charm": (regen_charm, [" G ", "GTG", " G "], {"G": "minecraft:gold_ingot", "T": "minecraft:ghast_tear"}),
    "magnet": (magnet, ["I I", "I I", "RGR"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "G": "minecraft:gold_ingot"}),
    "blast_ward": (blast_ward, [" O ", "OGO", " O "], {"O": "minecraft:obsidian", "G": "minecraft:gunpowder"}),
    "thorns_ring": (thorns_ring, [" C ", "CGC", " C "], {"C": "minecraft:cactus", "G": "minecraft:gold_ingot"}),
    "hunter_charm": (hunter_charm, [" B ", "BFB", " B "], {"B": "minecraft:bone", "F": "minecraft:rabbit_foot"}),
    "trinket_bag": (bag, ["SIS", "L L", "LLL"], {"S": "minecraft:string", "I": "minecraft:iron_ingot", "L": "minecraft:leather"}),
}

for name, (draw, pattern, key) in ITEMS.items():
    write(os.path.join(ASSETS, "items", name + ".json"),
          json.dumps({"model": {"type": "minecraft:model", "model": "combat:item/" + name}}) + "\n")
    write(os.path.join(ASSETS, "models", "item", name + ".json"),
          json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "combat:item/" + name}}) + "\n")
    write(os.path.join(DATA, "recipe", name + ".json"),
          json.dumps({"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern, "key": key,
                      "result": {"id": "combat:" + name, "count": 1}}) + "\n")
    png(os.path.join(ASSETS, "textures", "item", name + ".png"), draw())

LANG = {
    "en_us": {
        "item.combat.feather_charm": "Feather Charm",
        "item.combat.feather_charm.desc": "Negates fall damage.",
        "item.combat.night_vision_charm": "Night Vision Charm",
        "item.combat.night_vision_charm.desc": "Grants night vision.",
        "item.combat.gills_charm": "Gills Charm",
        "item.combat.gills_charm.desc": "Lets you breathe underwater.",
        "item.combat.skill_charm": "Skill Charm",
        "item.combat.skill_charm.desc": "Shapeshift abilities recharge 30% faster.",
        "item.combat.fire_ring": "Fire Ring",
        "item.combat.fire_ring.desc": "Immune to fire and lava damage.",
        "item.combat.speed_buckle": "Swift Buckle",
        "item.combat.speed_buckle.desc": "Movement speed +15%.",
        "item.combat.spring_insole": "Spring Insole",
        "item.combat.spring_insole.desc": "Jump much higher.",
        "item.combat.regen_charm": "Regeneration Charm",
        "item.combat.regen_charm.desc": "Slowly restores health: 1 every 3 seconds.",
        "item.combat.magnet": "Magnet",
        "item.combat.magnet.desc": "Pulls nearby dropped items to you.",
        "item.combat.blast_ward": "Blast Ward",
        "item.combat.blast_ward.desc": "Explosion damage is halved.",
        "item.combat.thorns_ring": "Thorns Ring",
        "item.combat.thorns_ring.desc": "Melee attackers take 30% of the damage back.",
        "item.combat.hunter_charm": "Hunter Charm",
        "item.combat.hunter_charm.desc": "Kills have a 50% chance to drop their loot twice.",
        "item.combat.trinket_bag": "Trinket Bag",
        "item.combat.trinket_bag.desc": "Carry it to unlock one more trinket slot.",
        "combat.trinket.hint": "Equip in the trinket screen.",
        "container.combat.trinkets": "Trinkets",
        "combat.trinkets.slots": "%s / %s slots",
        "combat.trinkets.hint": "Trinket bags carried: %s",
        "combat.upgrade.total": "Ore upgrades: %s / %s",
        "combat.bow.draw": "Draw speed +%s%%",
        "combat.bow.no_drop": "Arrows fly straight",
        "combat.bow.burn": "Flame arrows %s",
        "combat.bow.slow": "Slowing arrows %s",
        "combat.bow.explode": "Explosive arrows %s",
        "combat.bow.total": "Bow upgrades: %s / %s",
        "key.combat.trinkets": "Open trinkets",
        "key.category.combat.main": "Combat",
    },
    "zh_tw": {
        "item.combat.feather_charm": "羽毛護符",
        "item.combat.feather_charm.desc": "免疫摔落傷害。",
        "item.combat.night_vision_charm": "夜視護符",
        "item.combat.night_vision_charm.desc": "持續獲得夜視。",
        "item.combat.gills_charm": "鰓之護符",
        "item.combat.gills_charm.desc": "可在水下呼吸。",
        "item.combat.skill_charm": "技能護符",
        "item.combat.skill_charm.desc": "變身技能冷卻縮短 30%。",
        "item.combat.fire_ring": "抗火戒指",
        "item.combat.fire_ring.desc": "免疫火焰與岩漿傷害。",
        "item.combat.speed_buckle": "疾行靴扣",
        "item.combat.speed_buckle.desc": "移動速度 +15%。",
        "item.combat.spring_insole": "彈簧鞋墊",
        "item.combat.spring_insole.desc": "跳得更高。",
        "item.combat.regen_charm": "再生護符",
        "item.combat.regen_charm.desc": "緩慢回復生命：每 3 秒 1 點。",
        "item.combat.magnet": "磁鐵",
        "item.combat.magnet.desc": "把附近的掉落物吸向你。",
        "item.combat.blast_ward": "防爆護符",
        "item.combat.blast_ward.desc": "爆炸傷害減半。",
        "item.combat.thorns_ring": "荊棘戒指",
        "item.combat.thorns_ring.desc": "被近戰攻擊時反彈 30% 的傷害。",
        "item.combat.hunter_charm": "狩獵護符",
        "item.combat.hunter_charm.desc": "擊殺生物時有 50% 機率多掉一次戰利品。",
        "item.combat.trinket_bag": "飾品背包",
        "item.combat.trinket_bag.desc": "帶在身上就多開放一格飾品欄。",
        "combat.trinket.hint": "在飾品介面中裝備。",
        "container.combat.trinkets": "飾品",
        "combat.trinkets.slots": "%s / %s 格",
        "combat.trinkets.hint": "身上的飾品背包：%s",
        "combat.upgrade.total": "礦物強化：%s / %s",
        "combat.bow.draw": "拉弓速度 +%s%%",
        "combat.bow.no_drop": "箭矢不下墜",
        "combat.bow.burn": "火焰箭 %s",
        "combat.bow.slow": "緩速箭 %s",
        "combat.bow.explode": "爆裂箭 %s",
        "combat.bow.total": "弓箭強化：%s / %s",
        "key.combat.trinkets": "開啟飾品欄",
        "key.category.combat.main": "戰鬥",
    },
}
for code, entries in LANG.items():
    write(os.path.join(ASSETS, "lang", code + ".json"), json.dumps(entries, ensure_ascii=False, indent=2) + "\n")
