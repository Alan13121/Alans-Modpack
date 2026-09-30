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

def cell():
    px = canvas((84, 56, 104), 6)
    frame(px, (44, 28, 60), (200, 150, 230))
    for y in range(4, 12):
        for x in range(4, 12):
            put(px, x, y, (40, 24, 56) if (x in (4, 11) or y in (4, 11)) else (120, 80, 150))
    for x in range(6, 10): put(px, x, 8, (220, 190, 250))
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

def cable():
    px = canvas((196, 120, 60), 7, 6)
    for x in range(16):
        put(px, x, 0, (140, 80, 36)); put(px, x, 15, (140, 80, 36))
        if x % 4 < 2: put(px, x, 7, (240, 170, 90)); put(px, x, 8, (240, 170, 90))
    return px

for name, px in [("controller_side", controller_side()), ("controller_top", controller_top()),
                 ("terminal_front", terminal_front()), ("terminal_side", terminal_side()), ("terminal_top", terminal_top()),
                 ("cell", cell()), ("cable", cable()),
                 ("crafting_terminal_front", crafting_front()), ("crafting_terminal_top", crafting_top()),
                 ("input_interface", arrows((44, 86, 92), (24, 44, 50), (90, 230, 200), 8, True)),
                 ("output_interface", arrows((104, 74, 44), (56, 38, 22), (250, 170, 80), 9, False))]:
    target = os.path.join(OUT, name + ".png")
    # Never overwrite a texture that already exists (it may have been replaced by hand); pass --force to regenerate.
    if os.path.exists(target) and "--force" not in sys.argv:
        continue
    png(target, px)
print("textures written to", os.path.abspath(OUT))
