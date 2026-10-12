#!/usr/bin/env python3
"""Generates BeCom's textures in both styles (DESIGN.md 15.1, D53).

Every texture is drawn from simple shapes (panels, lamps, wires, insulators) on a 16x16 grid of roles, then rendered
twice: in GT's default palette into the jar's assets, and in a Modernity-like palette into
resourcepacks/modernity/. Machine overlays are transparent layers over GT's own casings, like GT's overlays; each
_GLOW layer holds only the lamps, lit, for when the machine works.

Run from the repository root: python3 tools/assets/textures.py
Optional: python3 tools/assets/textures.py --preview <dir> <gt-casing.png>... writes previews over GT casings.
"""

import os
import random
import sys

from PIL import Image

SIZE = 16
JAR = os.path.join("src", "main", "resources", "assets", "becom", "textures", "blocks")
PACK = os.path.join("resourcepacks", "modernity", "assets", "becom", "textures", "blocks")
JAR_ITEMS = os.path.join("src", "main", "resources", "assets", "becom", "textures", "items")
PACK_ITEMS = os.path.join("resourcepacks", "modernity", "assets", "becom", "textures", "items")

# Roles a pixel can play. Palettes give each a colour; anything without a role is transparent.
DEFAULT = {
    "o": (25, 29, 29),  # outline
    "p": (51, 51, 51),  # panel face
    "m": (71, 71, 71),  # panel bevel, lit edge
    "d": (38, 40, 40),  # panel bevel, shadow edge
    "h": (192, 192, 192),  # symbol
    "s": (128, 128, 128),  # symbol shade, traces
    "w": (25, 29, 29),  # wire
    "i": (176, 208, 224),  # glass insulator
    "j": (120, 160, 184),  # glass insulator, shade
    "l": (0, 64, 0),  # lamp, off
    "L": (64, 255, 64),  # lamp, lit (glow layers only)
    "k": (8, 8, 8),  # socket hole
    "r": (150, 150, 150),  # socket rim
    "g": (40, 60, 44),  # display, dark
    "G": (120, 200, 120),  # display, digits
    "t": (90, 16, 16),  # TX lamp, off
    "T": (255, 60, 50),  # TX lamp, lit
}
MODERNITY = {
    "o": (34, 33, 30),
    "p": (62, 60, 55),
    "m": (92, 88, 80),
    "d": (46, 44, 40),
    "h": (205, 200, 188),
    "s": (140, 135, 125),
    "w": (34, 33, 30),
    "i": (226, 220, 206),  # porcelain insulator
    "j": (170, 160, 140),
    "l": (20, 60, 70),  # LED, off
    "L": (40, 220, 240),  # LED, lit
    "k": (14, 13, 12),
    "r": (160, 154, 140),
    "g": (24, 40, 46),  # display, dark
    "G": (110, 210, 230),  # display, digits
    "t": (80, 30, 24),
    "T": (250, 110, 70),
}
LIT = {"L"}


class Canvas:
    def __init__(self):
        self.px = [[None] * SIZE for _ in range(SIZE)]

    def set(self, x, y, role):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            self.px[y][x] = role

    def rect(self, x0, y0, x1, y1, role):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, role)

    def panel(self, x0, y0, x1, y1):
        """A GT-style plate: dark outline, face, lit top-left bevel, shadowed bottom-right."""
        self.rect(x0, y0, x1, y1, "o")
        self.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, "p")
        self.rect(x0 + 1, y0 + 1, x1 - 1, y0 + 1, "m")
        self.rect(x0 + 1, y0 + 1, x0 + 1, y1 - 1, "m")
        self.rect(x0 + 1, y1 - 1, x1 - 1, y1 - 1, "d")
        self.rect(x1 - 1, y0 + 1, x1 - 1, y1 - 1, "d")

    def lamp(self, x, y, w=2, h=2):
        self.rect(x, y, x + w - 1, y + h - 1, "l")

    def render(self, palette, glow=False):
        img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        for y in range(SIZE):
            for x in range(SIZE):
                role = self.px[y][x]
                if role is None:
                    continue
                if glow:
                    if role != "l":
                        continue
                    role = "L"
                img.putpixel((x, y), palette[role] + (255,))
        return img

    def has_lamps(self):
        return any(role == "l" for row in self.px for role in row)


def pylon_head():
    """Two glass insulators carrying the line across a steel crossarm."""
    c = Canvas()
    c.rect(0, 2, 15, 2, "w")
    for cx in (2, 10):
        for y in (3, 5, 7, 9):
            c.rect(cx + 1, y, cx + 2, y, "j")
        for y in (4, 6, 8):
            c.rect(cx, y, cx + 3, y, "i")
            c.set(cx, y, "j")
        c.rect(cx + 1, 10, cx + 2, 10, "s")
    c.panel(1, 11, 14, 14)
    c.rect(3, 12, 12, 12, "s")
    return c


def repeater_head():
    """An amplifier: signal in on the left, out of the triangle's tip on the right, and a lamp."""
    c = Canvas()
    c.panel(1, 1, 14, 14)
    c.lamp(10, 3)
    for i, half in enumerate((0, 1, 2, 3, 2, 1, 0)):
        y = 5 + i
        c.rect(5, y, 5 + (3 - abs(3 - i)), y, "h")
    c.rect(2, 8, 4, 8, "s")
    c.rect(9, 8, 12, 8, "s")
    return c


def router_head():
    """A hub with four spokes to four lamps, one per direction a call can take."""
    c = Canvas()
    c.panel(1, 1, 14, 14)
    c.rect(7, 4, 8, 11, "s")
    c.rect(4, 7, 11, 8, "s")
    c.rect(6, 6, 9, 9, "h")
    c.rect(7, 7, 8, 8, "s")
    for x, y in ((3, 3), (11, 3), (3, 11), (11, 11)):
        c.lamp(x, y)
    return c


def line_terminal():
    """A junction block: two rows of screw terminals where the line lands, and a lamp."""
    c = Canvas()
    c.panel(1, 1, 14, 14)
    for y in (5, 9):
        for x in (3, 6, 9):
            c.rect(x, y, x + 1, y + 1, "r")
            c.set(x + 1, y + 1, "k")
        c.rect(3, y + 2, 10, y + 2, "d")
    c.rect(12, 6, 12, 11, "s")
    c.lamp(11, 3, 2, 1)
    return c


def antenna_head(tier):
    """On a plate like the other heads: a dipole on its mast, waves going out on both sides, a pip per tier."""

    def draw():
        c = Canvas()
        c.panel(1, 1, 14, 14)
        c.lamp(7, 3, 2, 1)
        c.rect(7, 4, 8, 10, "s")
        c.rect(4, 5, 11, 5, "h")
        for x in (3, 12):
            c.rect(x, 4, x, 8, "h")
        for x in (5, 10):
            c.rect(x, 7, x, 9, "s")
        c.rect(4, 11, 11, 11, "d")
        for n in range(tier):
            px = 8 - tier + 2 * n
            c.rect(px, 12, px, 12, "h")
        return c

    return draw


def transceiver():
    """A radio set: speaker grille, channel display, tuning knob and an on-air lamp."""
    c = Canvas()
    c.panel(1, 1, 14, 14)
    for y in range(4, 12, 2):
        for x in range(3, 8, 2):
            c.set(x, y, "k")
            c.set(x + 1, y + 1, "d")
    c.rect(9, 3, 12, 6, "o")
    c.rect(10, 4, 11, 5, "g")
    c.set(10, 4, "G")
    c.set(11, 5, "G")
    c.rect(9, 9, 10, 10, "r")
    c.set(10, 10, "s")
    c.lamp(12, 9, 1, 2)
    c.rect(9, 12, 12, 12, "s")
    return c


OVERLAYS = {
    "OVERLAY_PYLON_HEAD": pylon_head,
    "OVERLAY_REPEATER_HEAD": repeater_head,
    "OVERLAY_ROUTER_HEAD": router_head,
    "OVERLAY_LINE_TERMINAL": line_terminal,
    "OVERLAY_ANTENNA_T1": antenna_head(1),
    "OVERLAY_ANTENNA_T2": antenna_head(2),
    "OVERLAY_ANTENNA_T3": antenna_head(3),
    "OVERLAY_TRANSCEIVER": transceiver,
}


def walkie(transmitting):
    """A walkie-talkie: antenna, body, grille, a small display, the side push-to-talk button and its TX lamp."""
    c = Canvas()
    c.rect(10, 0, 10, 4, "o")
    c.set(10, 0, "s")
    c.panel(4, 4, 11, 15)
    c.rect(3, 7, 3, 9, "m")
    c.set(3, 9, "d")
    for y in (7, 9):
        for x in (6, 8):
            c.set(x, y, "k")
            c.set(x + 1, y, "s")
    c.rect(6, 11, 9, 12, "g")
    c.set(7, 11, "G")
    c.set(8, 12, "G")
    c.rect(8, 5, 9, 5, "T" if transmitting else "t")
    c.rect(6, 14, 9, 14, "s")
    return c


def signal_analyzer():
    """A handheld meter: a screen with signal bars, buttons, and a short antenna."""
    c = Canvas()
    c.rect(11, 0, 11, 2, "o")
    c.panel(3, 2, 12, 15)
    c.rect(5, 4, 10, 9, "g")
    for i, h in enumerate((1, 2, 3, 4)):
        x = 5 + i + (i // 2)
        c.rect(x, 9 - h, x, 8, "G")
    c.set(10, 5, "G")
    for x in (5, 8):
        c.rect(x, 11, x + 1, 12, "r")
        c.set(x + 1, 12, "s")
    c.rect(5, 14, 10, 14, "d")
    return c


ITEMS = {
    "walkie_talkie": lambda: walkie(False),
    "walkie_talkie_tx": lambda: walkie(True),
    "signal_analyzer": signal_analyzer,
}

# Block textures for BeCom's own models: base colour, shade, and highlight per style.
BAKELITE = {
    "default": ((36, 34, 34), (24, 23, 23), (70, 66, 64)),
    "modernity": ((58, 44, 36), (40, 30, 24), (96, 76, 62)),
}
DIAL = {
    "default": {"ring": (170, 170, 170), "face": (226, 222, 210), "hole": (20, 20, 20), "hub": (120, 120, 120)},
    "modernity": {"ring": (176, 166, 146), "face": (232, 224, 204), "hole": (30, 26, 22), "hub": (130, 120, 104)},
}
CABLE = {
    "default": ((178, 32, 30), (118, 18, 18), (214, 70, 64)),
    "modernity": ((168, 52, 40), (108, 32, 26), (204, 92, 74)),
}


def bakelite(style, glossy):
    base, shade, light = BAKELITE[style]
    rng = random.Random("bakelite-" + style + str(glossy))
    img = Image.new("RGBA", (SIZE, SIZE))
    for y in range(SIZE):
        for x in range(SIZE):
            v = rng.randint(-3, 3)
            color = base
            if x in (0, SIZE - 1) or y == SIZE - 1:
                color = shade
            elif y == 0:
                color = light
            elif glossy and y in (3, 4) and 2 <= x <= 13:
                color = tuple(min(255, (a + b) // 2) for a, b in zip(base, light))
            img.putpixel((x, y), tuple(max(0, min(255, ch + v)) for ch in color) + (255,))
    return img


def dial(style):
    pal = DIAL[style]
    img = Image.new("RGBA", (SIZE, SIZE), pal["ring"] + (255,))
    cx = cy = 7.5
    import math

    for y in range(SIZE):
        for x in range(SIZE):
            r = math.hypot(x - cx, y - cy)
            if r <= 6.5:
                img.putpixel((x, y), pal["face"] + (255,))
            if r <= 2.0:
                img.putpixel((x, y), pal["hub"] + (255,))
    # Ten finger holes round the dial, the gap at the bottom right where the finger stop sits.
    for n in range(10):
        a = math.radians(-60 - n * 27)
        hx = round(cx + 4.4 * math.cos(a))
        hy = round(cy - 4.4 * math.sin(a))
        img.putpixel((hx, hy), pal["hole"] + (255,))
    return img


def cable(style):
    base, shade, light = CABLE[style]
    img = Image.new("RGBA", (SIZE, SIZE))
    for y in range(SIZE):
        for x in range(SIZE):
            # Twisted insulation: a diagonal stripe every four pixels.
            color = shade if (x + y) % 4 == 0 else light if (x + y) % 4 == 2 else base
            img.putpixel((x, y), color + (255,))
    return img


def save(img, *parts):
    path = os.path.join(*parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return path


def generate():
    written = []
    for style, root, palette in (("default", JAR, DEFAULT), ("modernity", PACK, MODERNITY)):
        for name, draw in OVERLAYS.items():
            canvas = draw()
            written.append(save(canvas.render(palette), root, "iconsets", name + ".png"))
            if canvas.has_lamps():
                written.append(save(canvas.render(palette, glow=True), root, "iconsets", name + "_GLOW.png"))
        written.append(save(bakelite(style, False), root, "telephone_body.png"))
        written.append(save(bakelite(style, True), root, "telephone_handset.png"))
        written.append(save(dial(style), root, "telephone_dial.png"))
        written.append(save(cable(style), root, "phone_cable.png"))
    for root, palette in ((JAR_ITEMS, DEFAULT), (PACK_ITEMS, MODERNITY)):
        for name, draw in ITEMS.items():
            written.append(save(draw().render(palette), root, name + ".png"))
    return written


def preview(out_dir, casings):
    """Each overlay over each given GT casing, in both styles, scaled up: for looking at before committing."""
    os.makedirs(out_dir, exist_ok=True)
    rows = []
    for style, palette in (("default", DEFAULT), ("modernity", MODERNITY)):
        for casing_path in casings:
            casing = Image.open(casing_path).convert("RGBA").crop((0, 0, SIZE, SIZE))
            row = [casing]
            for draw in OVERLAYS.values():
                canvas = draw()
                tile = casing.copy()
                tile.alpha_composite(canvas.render(palette))
                row.append(tile)
                if canvas.has_lamps():
                    lit = tile.copy()
                    lit.alpha_composite(canvas.render(palette, glow=True))
                    row.append(lit)
            rows.append(row)
        rows.append(
            [bakelite(style, False), bakelite(style, True), dial(style), cable(style)]
            + [draw().render(palette) for draw in ITEMS.values()]
        )
    width = max(len(r) for r in rows) * (SIZE + 2)
    sheet = Image.new("RGBA", (width, len(rows) * (SIZE + 2)), (255, 0, 255, 255))
    for j, row in enumerate(rows):
        for i, tile in enumerate(row):
            sheet.paste(tile, (i * (SIZE + 2), j * (SIZE + 2)))
    sheet = sheet.resize((sheet.width * 8, sheet.height * 8), Image.NEAREST)
    path = os.path.join(out_dir, "textures_preview.png")
    sheet.save(path)
    return path


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        print(preview(sys.argv[2], sys.argv[3:]))
    else:
        for p in generate():
            print(p)
