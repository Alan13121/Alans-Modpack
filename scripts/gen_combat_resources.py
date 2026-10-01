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
