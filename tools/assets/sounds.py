#!/usr/bin/env python3
"""Synthesizes BeCom's sounds (DESIGN.md 15.2, D53) and encodes them to OGG Vorbis with ffmpeg.

The call tones follow North America's precise tone plan: dial tone 350 + 440 Hz, ringback 440 + 480 Hz, busy
480 + 620 Hz, and the three rising special information tones for an unreachable number. Looping tones are cut to a
whole number of cycles of every frequency in them, so they loop without a click.

Run from the repository root: python3 tools/assets/sounds.py
"""

import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = os.path.join("src", "main", "resources", "assets", "becom", "sounds")
# Vorbis quality 4 is about 128 kbps for stereo; plenty for mono tones.
QUALITY = "4"
TONE_LEVEL = 0.22
FADE = 0.008


def t(seconds):
    return np.arange(int(round(seconds * RATE))) / RATE


def tone(freqs, seconds, level=TONE_LEVEL):
    x = t(seconds)
    return sum(level * np.sin(2 * np.pi * f * x) for f in freqs)


def silence(seconds):
    return np.zeros(int(round(seconds * RATE)))


def fade(signal, seconds=FADE):
    n = min(len(signal) // 2, int(seconds * RATE))
    if n > 0:
        ramp = np.linspace(0, 1, n)
        signal = signal.copy()
        signal[:n] *= ramp
        signal[-n:] *= ramp[::-1]
    return signal


def cadence(freqs, on, off, cycles=1):
    """on seconds of the tone, off seconds of silence, repeated; faded at each edge so it doesn't click."""
    return np.concatenate([np.concatenate([fade(tone(freqs, on)), silence(off)]) for _ in range(cycles)])


def dial_tone():
    # One second holds 350 and 440 whole cycles, so the file loops seamlessly.
    return tone((350, 440), 1.0)


def ringback():
    return cadence((440, 480), 2.0, 4.0)


def busy():
    return cadence((480, 620), 0.5, 0.5)


def unreachable():
    # The three rising special information tones, then a short pause.
    parts = [fade(tone((f,), 0.33, 0.3)) for f in (950, 1400, 1800)]
    return np.concatenate(parts + [silence(0.3)])


def bell(fundamental, length):
    """One strike of a small bell: a few inharmonic partials, each dying away."""
    x = t(length)
    partials = ((1.0, 1.0, 0.06), (2.0, 0.5, 0.045), (2.76, 0.35, 0.03), (5.4, 0.15, 0.02))
    return sum(a * np.exp(-x / tau) * np.sin(2 * np.pi * fundamental * r * x) for r, a, tau in partials)


def ringtone():
    """A two-tone ring: a clapper striking two bells in turn, 20 times a second, for two seconds."""
    length = 2.0
    out = np.zeros(int(length * RATE) + RATE // 4)
    strike = 1 / 20
    for i in range(int(length / strike)):
        b = bell(1100 if i % 2 == 0 else 1320, 0.25)
        start = int(i * strike * RATE)
        out[start:start + len(b)] += b
    out = out[: int(length * RATE)]
    return fade(out / np.max(np.abs(out)) * 0.6, 0.05)


def buzz():
    """The silent-mode buzz: a short low rasp, like a vibrating phone on a desk."""
    x = t(0.35)
    raw = np.sign(np.sin(2 * np.pi * 160 * x)) * 0.25 + np.sin(2 * np.pi * 80 * x) * 0.2
    envelope = np.sin(np.pi * np.clip(x / 0.35, 0, 1))
    return fade(raw * envelope * (0.6 + 0.4 * np.sin(2 * np.pi * 24 * x)), 0.01)


def click():
    """The hang-up click: a short snap of filtered noise over a small thump."""
    rng = np.random.default_rng(7)
    x = t(0.06)
    noise = rng.normal(0, 1, len(x))
    # A one-pole low-pass keeps the snap from sounding like static.
    smooth = np.zeros_like(noise)
    for i in range(1, len(noise)):
        smooth[i] = smooth[i - 1] + 0.35 * (noise[i] - smooth[i - 1])
    thump = np.sin(2 * np.pi * 120 * x) * 0.6
    signal = (smooth * 0.5 + thump) * np.exp(-x / 0.012)
    return fade(signal / np.max(np.abs(signal)) * 0.7, 0.002)


def squelch():
    """The squelch tail: a short burst of band-limited hiss as a radio's receiver closes after a transmission."""
    rng = np.random.default_rng(11)
    x = t(0.2)
    noise = rng.normal(0, 1, len(x))
    spectrum = np.fft.rfft(noise)
    freqs = np.fft.rfftfreq(len(noise), 1 / RATE)
    # The same band as a radio channel at Medium (DESIGN.md 4.3), so it sounds like the channel it closes.
    spectrum[(freqs < 300) | (freqs > 3400)] = 0
    hiss = np.fft.irfft(spectrum, len(noise))
    envelope = np.minimum(1, x / 0.004) * np.exp(-x / 0.06)
    signal = hiss * envelope
    return fade(signal / np.max(np.abs(signal)) * 0.5, 0.004)


def rocket_launch():
    """A Satellite Rocket going up: a rumble of low-passed noise that rises in pitch and grows, then fades away."""
    rng = np.random.default_rng(23)
    length = 6.0
    x = t(length)
    noise = rng.normal(0, 1, len(x))
    # A one-pole low-pass, run twice for a steeper slope, whose cutoff climbs from a deep rumble to a roar.
    cutoff = 60 + 900 * (x / length) ** 1.5
    alpha = 1 - np.exp(-2 * np.pi * cutoff / RATE)
    signal = noise
    for _ in range(2):
        out = np.empty_like(signal)
        acc = 0.0
        for i in range(len(signal)):
            acc += alpha[i] * (signal[i] - acc)
            out[i] = acc
        signal = out
    # The engine catches over a second and a half, roars, then the rocket is gone in the last two seconds.
    envelope = np.minimum(1, x / 1.5) * np.clip((length - x) / 2.0, 0, 1)
    signal = signal * envelope
    return fade(signal / np.max(np.abs(signal)) * 0.8, 0.02)


# Sounds heard "at the ear", with no place in the world: the handset's tones and click, and the owner-only buzz.
# Minecraft plays them at (0, 0, 0) with no attenuation, and OpenAL pans a mono source toward its position, so these
# are written in stereo, which OpenAL never pans. Sounds that come from a block, like the ring, stay mono.
AT_THE_EAR = {"tone/dial", "tone/ringback", "tone/busy", "tone/unreachable", "phone/buzz", "phone/hangup"}

SOUNDS = {
    "tone/dial": dial_tone,
    "tone/ringback": ringback,
    "tone/busy": busy,
    "tone/unreachable": unreachable,
    "phone/ring": ringtone,
    "phone/buzz": buzz,
    "phone/hangup": click,
    "radio/squelch": squelch,
    "rocket/launch": rocket_launch,
}


def write_ogg(name, signal):
    path = os.path.join(OUT, name + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    channels = 2 if name in AT_THE_EAR else 1
    pcm = (np.clip(signal, -1, 1) * 32767).astype("<i2")
    if channels == 2:
        pcm = np.repeat(pcm, 2)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "sound.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(channels)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        # -bitexact and no metadata keep the output identical between runs.
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", QUALITY, "-map_metadata",
             "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", path],
            check=True)
    return path


if __name__ == "__main__":
    for name, make in SOUNDS.items():
        print(write_ogg(name, make()))
