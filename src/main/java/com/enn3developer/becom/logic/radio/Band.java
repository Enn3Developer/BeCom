package com.enn3developer.becom.logic.radio;

/**
 * A radio band (DESIGN.md 6.1): VHF for radio channels, UHF for calls. What differs is the wavelength, which sets how
 * well a signal bends over hills, and how much each block of cover costs.
 */
public record Band(String name, double lambda, double coverPerBlock) {}
