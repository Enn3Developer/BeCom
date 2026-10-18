#!/usr/bin/env python3
"""Generates BeCom's textures in both styles.

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
    "f": (140, 84, 196),  # fluix, AE2's crystal; a portal's glow
    "e": (92, 52, 132),  # fluix, shade
    "b": (34, 46, 98),  # solar cell
    "B": (86, 110, 176),  # solar cell, highlight
    "1": (200, 200, 200),  # LV mark
    "2": (230, 120, 40),  # MV mark
    "3": (236, 210, 50),  # HV mark
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
    "f": (150, 120, 196),
    "e": (100, 80, 140),
    "b": (30, 44, 70),
    "B": (90, 120, 150),
    "1": (205, 200, 188),
    "2": (214, 124, 60),
    "3": (220, 196, 80),
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


def router():
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


def exchange():
    """A switchboard: two rows of jacks, each under its lamp, and a patch cord joining two of them in a call."""
    c = Canvas()
    c.panel(1, 1, 14, 14)
    for y in (4, 8):
        for x in (3, 6, 9):
            c.lamp(x, y - 1, 2, 1)
            c.rect(x, y, x + 1, y + 1, "r")
            c.set(x + 1, y + 1, "k")
    c.rect(4, 10, 4, 11, "h")
    c.rect(4, 12, 10, 12, "h")
    c.rect(10, 10, 10, 11, "h")
    c.rect(12, 3, 12, 12, "s")
    return c


def line_hatch():
    """A cable socket: a rimmed port the line plugs into, its pins round the conductor, and a lamp."""
    c = Canvas()
    c.panel(1, 1, 14, 14)
    c.rect(4, 3, 11, 10, "r")
    c.rect(5, 4, 10, 9, "k")
    for x, y in ((6, 5), (9, 5), (6, 8), (9, 8)):
        c.set(x, y, "s")
    c.rect(7, 6, 8, 7, "h")
    c.rect(4, 11, 11, 11, "d")
    c.lamp(7, 12, 2, 1)
    return c


def switching_rack(tier):
    """A rack cabinet: four switching cards on shelves, each with its tier's mark and a lamp lit while calls use it."""

    def draw():
        c = Canvas()
        c.panel(1, 1, 14, 14)
        for y in (3, 6, 9, 12):
            c.rect(3, y, 12, y + 1, "d")
            c.rect(3, y, 3, y + 1, str(tier))
            c.lamp(5, y, 2, 1)
            c.rect(8, y + 1, 12, y + 1, "s")
        return c

    return draw


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


def me_bridge():
    """An ME Bridge: AE2's fluix crystal, a wire across to BeCom's radio waves, and a lamp."""
    c = Canvas()
    c.panel(1, 1, 14, 14)
    crystal = [(5, 4), (4, 5), (5, 5), (6, 5), (3, 6), (4, 6), (5, 6), (6, 6), (7, 6), (4, 7), (5, 7), (6, 7), (5, 8)]
    for x, y in crystal:
        c.set(x, y, "f")
    for x, y in ((6, 6), (6, 7), (5, 8), (7, 6), (6, 5)):
        c.set(x, y, "e")
    c.rect(8, 6, 9, 6, "s")
    for x, y in ((10, 5), (11, 6), (10, 7)):
        c.set(x, y, "h")
    for x, y in ((12, 4), (13, 5), (13, 6), (13, 7), (12, 8)):
        c.set(x, y, "h")
    c.lamp(3, 11, 2, 1)
    c.rect(7, 11, 12, 11, "s")
    return c


def videophone():
    """A Videophone: a camera lens over a screen showing someone's head and shoulders, a speaker grille and a lamp."""
    c = Canvas()
    c.panel(1, 1, 14, 14)
    c.rect(6, 3, 9, 3, "r")
    c.rect(7, 3, 8, 3, "k")
    c.rect(3, 5, 12, 10, "o")
    c.rect(4, 6, 11, 9, "g")
    # Head, a dark gap for the neck, then rounded shoulders.
    c.rect(7, 6, 8, 7, "G")
    c.set(6, 8, "G")
    c.set(9, 8, "G")
    c.rect(5, 9, 10, 9, "G")
    for x in range(4, 10, 2):
        c.set(x, 12, "k")
        c.set(x + 1, 12, "d")
    c.lamp(11, 12, 2, 1)
    return c


def launch_pad():
    """A Satellite Rocket standing on its pad, gantry lights either side; the flame under it lights during a launch."""
    c = Canvas()
    c.panel(1, 1, 14, 14)
    c.rect(7, 2, 8, 2, "h")
    c.rect(6, 3, 9, 9, "h")
    c.rect(7, 5, 8, 5, "s")
    c.rect(9, 4, 9, 9, "s")
    for y in range(7, 11):
        c.set(5, y, "s")
        c.set(10, y, "s")
    c.set(4, 10, "s")
    c.set(11, 10, "s")
    c.rect(7, 10, 8, 10, "d")
    c.lamp(7, 11, 2, 1)
    c.rect(3, 12, 12, 12, "s")
    c.rect(3, 13, 12, 13, "d")
    c.lamp(3, 3, 1, 2)
    c.lamp(12, 3, 1, 2)
    return c


def satellite_dish():
    """A dish facing the sky, its feed arm reaching to the horn at the focus, on a short stand, and a lamp."""
    c = Canvas()
    c.panel(1, 1, 14, 14)
    # The dish, seen from the side: a curve opening up and to the right.
    for x, y in ((3, 4), (3, 5), (3, 6), (4, 7), (4, 8), (5, 9), (6, 10), (7, 10), (8, 10), (9, 9)):
        c.set(x, y, "h")
    for x, y in ((4, 5), (4, 6), (5, 8), (6, 9), (7, 9)):
        c.set(x, y, "s")
    for x, y in ((6, 8), (7, 7), (8, 6), (9, 5)):
        c.set(x, y, "s")
    c.rect(10, 3, 11, 4, "h")
    c.rect(6, 11, 6, 12, "s")
    c.rect(4, 12, 8, 12, "d")
    c.lamp(11, 11, 2, 1)
    return c


def portal_relay():
    """A portal in its frame, the signal going out of it as waves, and a lamp."""
    c = Canvas()
    c.panel(1, 1, 14, 14)
    c.rect(3, 3, 7, 12, "o")
    c.rect(4, 4, 6, 11, "f")
    for x, y in ((4, 5), (5, 7), (6, 6), (4, 9), (6, 10), (5, 4)):
        c.set(x, y, "e")
    for x, y in ((9, 6), (10, 7), (10, 8), (9, 9)):
        c.set(x, y, "h")
    for x, y in ((11, 4), (12, 5), (12, 6), (12, 7), (12, 8), (12, 9), (11, 10)):
        c.set(x, y, "h")
    c.lamp(9, 12, 2, 1)
    return c


def app_mirror():
    """An App Store Mirror: a server's face, three drive bays each with its activity lamp, over a vent."""
    c = Canvas()
    c.panel(1, 1, 14, 14)
    for y in (3, 6, 9):
        c.rect(3, y, 12, y + 1, "o")
        c.rect(4, y, 9, y, "s")
        c.rect(4, y + 1, 9, y + 1, "d")
        c.lamp(11, y, 1, 2)
    for x in range(4, 12, 2):
        c.set(x, 12, "k")
    return c


OVERLAYS = {
    "OVERLAY_EXCHANGE": exchange,
    "OVERLAY_ROUTER": router,
    "OVERLAY_LINE_HATCH": line_hatch,
    "OVERLAY_SWITCHING_RACK_LV": switching_rack(1),
    "OVERLAY_SWITCHING_RACK_MV": switching_rack(2),
    "OVERLAY_SWITCHING_RACK_HV": switching_rack(3),
    "OVERLAY_ANTENNA_T1": antenna_head(1),
    "OVERLAY_ANTENNA_T2": antenna_head(2),
    "OVERLAY_ANTENNA_T3": antenna_head(3),
    "OVERLAY_TRANSCEIVER": transceiver,
    "OVERLAY_ME_BRIDGE": me_bridge,
    "OVERLAY_VIDEOPHONE": videophone,
    "OVERLAY_LAUNCH_PAD": launch_pad,
    "OVERLAY_SATELLITE_DISH": satellite_dish,
    "OVERLAY_PORTAL_RELAY": portal_relay,
    "OVERLAY_APP_MIRROR": app_mirror,
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


def mobile_phone():
    """A mobile phone: a slim body, the earpiece, a large screen with signal bars and two lines, and a home button."""
    c = Canvas()
    c.panel(4, 1, 11, 15)
    c.rect(7, 2, 8, 2, "k")
    c.rect(5, 3, 10, 11, "g")
    for i, h in enumerate((1, 2, 3)):
        c.rect(6 + i, 6 - h, 6 + i, 5, "G")
    c.rect(6, 8, 9, 8, "G")
    c.rect(6, 10, 8, 10, "s")
    c.rect(7, 13, 8, 13, "r")
    c.set(8, 13, "s")
    return c


def me_phone():
    """The ME Phone: the mobile phone in fluix trim, an ME crystal on its screen."""
    c = mobile_phone()
    for y in range(2, 15):
        c.set(4, y, "f")
        c.set(11, y, "e")
    c.rect(6, 8, 9, 10, "g")
    for x, y in ((7, 8), (6, 9), (7, 9), (8, 9), (7, 10)):
        c.set(x, y, "f")
    return c


def satellite():
    """A satellite: its body between two solar panel wings, and an antenna dish on top."""
    c = Canvas()
    for x0 in (0, 10):
        c.rect(x0, 5, x0 + 5, 10, "o")
        c.rect(x0 + 1, 6, x0 + 4, 9, "b")
        c.rect(x0 + 1, 6, x0 + 4, 6, "B")
        c.set(x0 + 2, 8, "B")
    c.panel(6, 5, 9, 11)
    c.rect(7, 2, 8, 2, "h")
    c.rect(6, 1, 6, 1, "s")
    c.rect(9, 1, 9, 1, "s")
    c.rect(7, 3, 8, 4, "s")
    c.rect(7, 12, 8, 13, "s")
    return c


def satellite_rocket():
    """A Satellite Rocket: nose cone, a hull with a porthole, fins and the engine nozzle."""
    c = Canvas()
    c.rect(7, 0, 8, 0, "h")
    c.rect(6, 1, 9, 2, "h")
    c.panel(5, 3, 10, 12)
    c.rect(7, 5, 8, 6, "i")
    c.set(8, 6, "j")
    for y in range(9, 14):
        c.set(4, y, "s")
        c.set(11, y, "s")
    c.set(3, 13, "s")
    c.set(12, 13, "s")
    c.rect(6, 13, 9, 13, "d")
    c.rect(7, 14, 8, 15, "k")
    return c


def rocket_radio_receiver():
    """The Rocket Radio Receiver: a rugged box with a little dish on top and a status display."""
    c = Canvas()
    c.rect(7, 3, 7, 6, "s")
    for x, y in ((4, 1), (5, 2), (6, 3), (8, 3), (9, 2), (10, 1)):
        c.set(x, y, "h")
    c.panel(2, 7, 13, 15)
    c.rect(4, 9, 11, 12, "g")
    for i, h in enumerate((1, 2, 3)):
        c.rect(5 + i, 12 - h, 5 + i, 11, "G")
    c.rect(9, 10, 10, 10, "G")
    c.rect(4, 14, 11, 14, "d")
    return c


ITEMS = {
    "walkie_talkie": lambda: walkie(False),
    "walkie_talkie_tx": lambda: walkie(True),
    "signal_analyzer": signal_analyzer,
    "mobile_phone": mobile_phone,
    "me_phone": me_phone,
    "satellite": satellite,
    "satellite_rocket": satellite_rocket,
    "rocket_radio_receiver": rocket_radio_receiver,
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
# Each cable's insulation, by tier: Phone Cable copper-orange, Coaxial grey, Fibre blue.
CABLE = {
    "default": {
        "phone_cable": ((224, 138, 58), (138, 74, 26), (244, 176, 112)),
        "coaxial_cable": ((154, 154, 154), (90, 90, 90), (192, 192, 192)),
        "fibre_cable": ((90, 160, 208), (42, 104, 144), (138, 208, 255)),
    },
    "modernity": {
        "phone_cable": ((204, 128, 64), (130, 76, 34), (230, 166, 104)),
        "coaxial_cable": ((150, 146, 138), (92, 90, 84), (186, 180, 170)),
        "fibre_cable": ((96, 150, 186), (50, 98, 128), (140, 192, 226)),
    },
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


def cable(style, name):
    base, shade, light = CABLE[style][name]
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
        for name in CABLE[style]:
            written.append(save(cable(style, name), root, name + ".png"))
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
            [bakelite(style, False), bakelite(style, True), dial(style)]
            + [cable(style, name) for name in CABLE[style]]
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
