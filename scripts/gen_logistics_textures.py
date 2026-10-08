#!/usr/bin/env python3
"""Generates the 16x16 block textures for the logistics mod (no image libraries needed).
Only writes files that do not exist yet; use --force to regenerate all of them."""
import os, struct, sys, zlib, random

OUT = os.path.join(os.path.dirname(__file__), "..", "mods", "logistics", "src", "main", "resources",
                   "assets", "logistics", "textures", "block")

def png(path, px):
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

def frame(px, edge, corner=None):
    for i in range(16):
        for a, b in ((0, i), (15, i), (i, 0), (i, 15)):
            px[a][b] = list(edge) + [255]
    if corner:
        for a, b in ((1, 1), (1, 14), (14, 1), (14, 14)):
            px[a][b] = list(corner) + [255]

def put(px, x, y, c):
    px[y][x] = list(c) + [255]

def controller_side():
    px = canvas((52, 74, 96), 1)
    frame(px, (28, 40, 56), (120, 200, 230))
    for x in range(3, 13): put(px, x, 5, (90, 200, 235)); put(px, x, 10, (90, 200, 235))
    for y in range(5, 11): put(px, 3, y, (90, 200, 235)); put(px, 12, y, (90, 200, 235))
    for y in range(6, 10):
        for x in range(6, 10): put(px, x, y, (170, 235, 255))
    return px

def controller_top():
    px = canvas((40, 58, 78), 2)
    frame(px, (24, 34, 48), (120, 200, 230))
    for y in range(4, 12):
        for x in range(4, 12): put(px, x, y, (30, 46, 62))
    for y in range(6, 10):
        for x in range(6, 10): put(px, x, y, (110, 220, 250))
    return px

def terminal_front():
    px = canvas((60, 66, 74), 3)
    frame(px, (30, 34, 40))
    for y in range(2, 11):
        for x in range(2, 14): put(px, x, y, (14, 28, 36))
    for y in range(3, 10):
        for x in range(3, 13):
            if (x + y) % 3 == 0: put(px, x, y, (60, 190, 230))
    for x in range(3, 13): put(px, x, 3, (110, 220, 250))
    for x in range(4, 12): put(px, x, 12, (30, 34, 40))
    put(px, 12, 13, (90, 220, 130))
    return px

def terminal_side():
    px = canvas((60, 66, 74), 4)
    frame(px, (30, 34, 40))
    for x in range(3, 13): put(px, x, 8, (40, 46, 54))
    return px

def terminal_top():
    px = canvas((70, 78, 88), 5)
    frame(px, (30, 34, 40), (110, 220, 250))
    return px

def cell(base=(84, 56, 104), edge=(44, 28, 60), corner=(200, 150, 230), dark=(40, 24, 56), light=(120, 80, 150), glow=(220, 190, 250), seed=6, dots=0):
    px = canvas(base, seed)
    frame(px, edge, corner)
    for y in range(4, 12):
        for x in range(4, 12):
            put(px, x, y, dark if (x in (4, 11) or y in (4, 11)) else light)
    for x in range(6, 10): put(px, x, 8, glow)
    # one extra lit pip per tier above the first, so tiers are easy to tell apart
    for i in range(dots): put(px, 6 + i * 2, 6, glow); put(px, 6 + i * 2, 10, glow)
    return px

def arrows(base, edge, glow, seed, inward):
    px = canvas(base, seed)
    frame(px, edge, glow)
    for y in range(4, 12):
        for x in range(4, 12): put(px, x, y, (22, 30, 38))
    # a chevron pointing into (inward) or out of the centre
    for i in range(4):
        for x, y in ((3 + i, 4 + i), (12 - i, 4 + i)) if inward else ((3 + i, 11 - i), (12 - i, 11 - i)):
            put(px, x, y, glow)
    for y in range(6, 10):
        for x in range(7, 9): put(px, x, y, glow)
    return px

def storage_interface():
    px = canvas((50, 82, 96), 8)
    frame(px, (26, 44, 54), (120, 235, 215))
    for y in range(4, 12):
        for x in range(4, 12): put(px, x, y, (22, 30, 38))
    # chevrons pointing in at the top and out at the bottom
    for i in range(3):
        for x, y in ((4 + i, 4 + i), (11 - i, 4 + i)): put(px, x, y, (90, 230, 200))
        for x, y in ((4 + i, 11 - i), (11 - i, 11 - i)): put(px, x, y, (250, 170, 80))
    for y in range(5, 11):
        for x in range(7, 9): put(px, x, y, (200, 235, 225))
    return px

def conduit(base, stripe, seed):
    px = canvas(base, seed, 6)
    for x in range(16):
        put(px, x, 0, tuple(int(c * 0.7) for c in base)); put(px, x, 15, tuple(int(c * 0.7) for c in base))
        if x % 4 < 2: put(px, x, 7, stripe); put(px, x, 8, stripe)
    return px

def crafting_front():
    px = canvas((70, 56, 44), 10)
    frame(px, (36, 28, 22))
    for y in range(2, 11):
        for x in range(2, 14): put(px, x, y, (24, 20, 16))
    for gy in range(3):
        for gx in range(3):
            for dy in range(2):
                for dx in range(2): put(px, 3 + gx * 3 + dx, 3 + gy * 3 + dy, (230, 190, 120) if (gx + gy) % 2 == 0 else (150, 110, 70))
    for x in range(4, 12): put(px, x, 12, (36, 28, 22))
    put(px, 12, 13, (90, 220, 130))
    return px

def crafting_top():
    px = canvas((120, 92, 60), 11)
    frame(px, (60, 44, 28), (230, 190, 120))
    for i in range(1, 15):
        put(px, 5, i, (70, 52, 34)); put(px, 10, i, (70, 52, 34)); put(px, i, 5, (70, 52, 34)); put(px, i, 10, (70, 52, 34))
    return px

def farm_interface():
    px = canvas((60, 96, 56), 15)
    frame(px, (30, 52, 28), (150, 230, 110))
    for y in range(4, 12):
        for x in range(4, 12): put(px, x, y, (24, 40, 22))
    # a sprout: stem and two leaves
    for y in range(7, 12): put(px, 8, y, (110, 200, 80))
    for i in range(3): put(px, 7 - i, 7 - i, (140, 230, 100)); put(px, 9 + i, 6 - i, (140, 230, 100))
    return px

def autocrafter_front():
    px = canvas((96, 72, 50), 16)
    frame(px, (54, 40, 26), (240, 200, 130))
    for y in range(3, 13):
        for x in range(3, 13): put(px, x, y, (30, 24, 18))
    for gy in range(3):
        for gx in range(3):
            put(px, 4 + gx * 3, 4 + gy * 3, (240, 200, 130)); put(px, 5 + gx * 3, 4 + gy * 3, (240, 200, 130))
    for x in range(4, 12): put(px, x, 12, (120, 200, 255))
    return px

def cable():
    px = canvas((196, 120, 60), 7, 6)
    for x in range(16):
        put(px, x, 0, (140, 80, 36)); put(px, x, 15, (140, 80, 36))
        if x % 4 < 2: put(px, x, 7, (240, 170, 90)); put(px, x, 8, (240, 170, 90))
    return px

def channel_block():
    px = canvas((64, 110, 72), 20)
    frame(px, (30, 56, 36), (150, 240, 150))
    for y in range(4, 12):
        for x in range(4, 12): put(px, x, y, (20, 40, 26))
    for i in range(3, 13):
        put(px, i, 8, (140, 240, 150)); put(px, 8, i, (140, 240, 150))
    for a, b in ((5, 5), (10, 5), (5, 10), (10, 10)): put(px, a, b, (210, 255, 200))
    return px

def antenna():
    px = canvas((150, 160, 170), 21)
    frame(px, (80, 88, 96), (240, 250, 255))
    for y in range(2, 14): put(px, 8, y, (60, 66, 74)); put(px, 7, y, (200, 210, 220))
    for r in (3, 5):
        for d in range(-r, r + 1):
            if abs(d) in (r,): put(px, 8 + d, 4, (120, 220, 255))
    for x in range(5, 12): put(px, x, 13, (60, 66, 74))
    put(px, 8, 2, (255, 90, 90))
    return px

def solar_top():
    px = canvas((30, 50, 100), 22, 6)
    frame(px, (150, 160, 175), (230, 240, 255))
    for y in range(2, 14):
        for x in range(2, 14):
            if x % 4 == 1 or y % 4 == 1: put(px, x, y, (110, 130, 170))
            elif (x + y) % 5 == 0: put(px, x, y, (80, 120, 220))
    return px

def solar_side():
    px = canvas((84, 90, 100), 23)
    frame(px, (44, 48, 56))
    for x in range(3, 13): put(px, x, 8, (240, 210, 90))
    return px

def coal_generator():
    px = canvas((58, 56, 58), 24)
    frame(px, (28, 28, 30), (230, 150, 70))
    for y in range(4, 12):
        for x in range(4, 12): put(px, x, y, (16, 14, 14))
    for y in range(7, 12):
        for x in range(5, 11):
            if (x * 3 + y) % 4 != 0: put(px, x, y, (240, 150, 40) if y > 8 else (255, 210, 90))
    return px

def chunk_loader():
    px = canvas((96, 50, 96), 27)
    frame(px, (54, 26, 54), (240, 150, 240))
    for y in range(3, 13):
        for x in range(3, 13): put(px, x, y, (30, 16, 30))
    for i in range(3, 13):
        put(px, i, 6, (230, 140, 230)); put(px, i, 9, (230, 140, 230)); put(px, 6, i, (230, 140, 230)); put(px, 9, i, (230, 140, 230))
    put(px, 7, 7, (255, 230, 255)); put(px, 8, 8, (255, 230, 255)); put(px, 7, 8, (255, 230, 255)); put(px, 8, 7, (255, 230, 255))
    return px

def teleporter_top():
    px = canvas((70, 40, 110), 25)
    frame(px, (36, 20, 60), (210, 160, 255))
    for y in range(3, 13):
        for x in range(3, 13):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            if 2 < d < 3.2 or 4.6 < d < 5.4: put(px, x, y, (200, 140, 255))
            elif d <= 2: put(px, x, y, (240, 220, 255))
    return px

def teleporter_side():
    px = canvas((62, 36, 98), 26)
    frame(px, (36, 20, 60))
    for x in range(3, 13): put(px, x, 3, (190, 130, 250)); put(px, x, 12, (190, 130, 250))
    return px

def item_card():
    px = [[[0, 0, 0, 0] for _ in range(16)] for _ in range(16)]
    for y in range(3, 13):
        for x in range(2, 14): put(px, x, y, (40, 120, 90))
    for x in range(2, 14): put(px, x, 3, (150, 240, 190)); put(px, x, 12, (20, 70, 52))
    for y in range(3, 13): put(px, 2, y, (150, 240, 190)); put(px, 13, y, (20, 70, 52))
    for y in range(6, 10):
        for x in range(4, 8): put(px, x, y, (240, 220, 110))
    for x in range(9, 12): put(px, x, 6, (200, 250, 220)); put(px, x, 8, (200, 250, 220))
    return px

def item_wireless():
    px = [[[0, 0, 0, 0] for _ in range(16)] for _ in range(16)]
    for y in range(4, 15):
        for x in range(4, 12): put(px, x, y, (70, 78, 90))
    for y in range(5, 10):
        for x in range(5, 11): put(px, x, y, (14, 28, 36))
    for x in range(5, 11): put(px, x, 5, (110, 220, 250))
    for y in range(11, 14):
        for x in (5, 7, 9): put(px, x, y, (140, 150, 160))
    for y in range(0, 4): put(px, 10, y, (200, 210, 220))
    put(px, 10, 0, (255, 90, 90))
    for x in (8, 12): put(px, x, 1, (120, 220, 255)); put(px, x - 1 if x == 8 else x + 1, 0, (120, 220, 255))
    return px

for name, px in [("controller_side", controller_side()), ("controller_top", controller_top()),
                 ("terminal_front", terminal_front()), ("terminal_side", terminal_side()), ("terminal_top", terminal_top()),
                 ("cell", cell()), ("cable", cable()),
                 ("cell_2", cell((50, 84, 120), (24, 44, 70), (130, 190, 240), (20, 36, 60), (70, 120, 170), (190, 225, 255), 12, 1)),
                 ("cell_3", cell((150, 120, 44), (84, 64, 20), (255, 220, 110), (70, 52, 14), (190, 155, 60), (255, 240, 170), 13, 2)),
                 ("cell_4", cell((196, 204, 210), (110, 118, 126), (255, 255, 255), (90, 98, 108), (226, 232, 238), (120, 220, 255), 14, 3)),
                 ("crafting_terminal_front", crafting_front()), ("crafting_terminal_top", crafting_top()),
                 ("farm_interface", farm_interface()), ("autocrafter", autocrafter_front()),
                 ("storage_interface", storage_interface()),
                 ("conduit", conduit((140, 150, 160), (120, 235, 215), 30)),
                 ("conduit_in", conduit((50, 150, 170), (190, 255, 245), 31)),
                 ("conduit_out", conduit((200, 130, 50), (255, 225, 150), 32)),
                 ("channel", channel_block()), ("antenna", antenna()), ("solar_top", solar_top()), ("solar_side", solar_side()),
                 ("coal_generator", coal_generator()), ("chunk_loader", chunk_loader()), ("teleporter_top", teleporter_top()), ("teleporter_side", teleporter_side()),
                 ("../item/channel_card", item_card()), ("../item/wireless_terminal", item_wireless())]:
    target = os.path.join(OUT, name + ".png")
    # Never overwrite a texture that already exists (it may have been replaced by hand); pass --force to regenerate.
    if os.path.exists(target) and "--force" not in sys.argv:
        continue
    png(target, px)
print("textures written to", os.path.abspath(OUT))
