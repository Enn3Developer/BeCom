package com.enn3developer.becom.logic;

/**
 * Every server-side tunable from DESIGN.md 17 that the code reads, as loaded from the config at startup (DESIGN.md
 * 14.2). Immutable and free of Minecraft types, so logic and tests can take it as-is.
 */
public record Tunables(
    // Signal (DESIGN.md 4)
    double levelLow, double levelMedium, double levelHigh, double levelMax, double levelHysteresis,
    double dropoutMaxLoss, double dropoutFullLossSnr, double dropoutCleanSnr, double dropoutMeanBurstMillis,
    double dropAfterSeconds,
    // Wired network (DESIGN.md 5)
    int pylonMinHeight, int pylonMaxHeight, double pylonSpanFactor, int pylonClearanceSeconds, double lineBudget,
    double lineLossPerBlock, int phoneCableLength, int terminalPower, int repeaterPower, int routerPower,
    // Calls (DESIGN.md 8.2, 10, 12)
    double directHearingRange, int sessionGraceSeconds, int halfLinkSeconds, int ringSeconds, int callAttemptsPerMinute,
    // Quests (DESIGN.md 15.5)
    boolean questlineEnabled, int questlineRecheckSeconds) {

    /** The values DESIGN.md 17 starts from. */
    public static Tunables defaults() {
        return new Tunables(
            6,
            12,
            18,
            24,
            2,
            0.30,
            6,
            12,
            60,
            3,
            6,
            16,
            4,
            60,
            30,
            0.08,
            32,
            4,
            4,
            16,
            16,
            10,
            5 * 60,
            30,
            5,
            true,
            60);
    }

    public LevelScale levelScale() {
        return new LevelScale(levelLow, levelMedium, levelHigh, levelMax, levelHysteresis);
    }

    public DropoutModel dropoutModel(double frameMillis) {
        return new DropoutModel(
            dropoutMaxLoss,
            dropoutFullLossSnr,
            dropoutCleanSnr,
            dropoutMeanBurstMillis,
            frameMillis);
    }

    public LineModel lineModel() {
        return new LineModel(lineBudget, lineLossPerBlock);
    }
}
