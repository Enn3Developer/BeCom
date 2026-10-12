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

    // Ceilings no sensible config reaches, which keep a hostile server's values from hanging a client: no frame column
    // is taller than the world, and a terminal's cable scan stays bounded.
    private static final int MAX_COLUMN = 255;
    private static final int MAX_CABLE = 256;

    /** GTNH Voice's frame length, in ms: the dropout model's step. Fixed by the voice codec, not a tunable. */
    public static final int FRAME_MILLIS = 20;

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

    /**
     * Refuses values that would crash the server at start or make it do something every tick, naming the config key.
     *
     * @throws IllegalArgumentException for the first such value
     */
    public void validate() {
        try {
            levelScale();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("levelLow, levelMedium, levelHigh, levelMax: " + e.getMessage(), e);
        }
        try {
            dropoutModel(FRAME_MILLIS);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "dropoutMaxLoss, dropoutFullLossSnr, dropoutCleanSnr, dropoutMeanBurstMillis (at least one "
                    + FRAME_MILLIS
                    + " ms voice frame): "
                    + e.getMessage(),
                e);
        }
        if (pylonMinHeight < 1) throw invalid("pylonMinHeight", pylonMinHeight, "at least 1");
        if (pylonMaxHeight < pylonMinHeight) throw invalid("pylonMaxHeight", pylonMaxHeight, "at least pylonMinHeight");
        if (pylonMaxHeight > MAX_COLUMN) throw invalid("pylonMaxHeight", pylonMaxHeight, "at most " + MAX_COLUMN);
        if (phoneCableLength < 1 || phoneCableLength > MAX_CABLE) {
            throw invalid("phoneCableLength", phoneCableLength, "1 to " + MAX_CABLE);
        }
        if (pylonSpanFactor <= 0) throw invalid("pylonSpanFactor", pylonSpanFactor, "more than 0");
        if (pylonClearanceSeconds < 1) throw invalid("pylonClearanceSeconds", pylonClearanceSeconds, "at least 1");
        if (lineLossPerBlock < 0) throw invalid("lineLossPerBlock", lineLossPerBlock, "0 or more");
        if (dropAfterSeconds < 0) throw invalid("dropAfterSeconds", dropAfterSeconds, "0 or more");
        if (directHearingRange < 0) throw invalid("directHearingRange", directHearingRange, "0 or more");
        if (ringSeconds < 1) throw invalid("ringSeconds", ringSeconds, "at least 1");
        if (callAttemptsPerMinute < 1) throw invalid("callAttemptsPerMinute", callAttemptsPerMinute, "at least 1");
        if (questlineRecheckSeconds < 1)
            throw invalid("questlineRecheckSeconds", questlineRecheckSeconds, "at least 1");
    }

    private static IllegalArgumentException invalid(String key, Object value, String rule) {
        return new IllegalArgumentException(key + " is " + value + ", but must be " + rule);
    }

    /** Every value by its name, for sending to clients (DESIGN.md 14.2). Doubles, ints and booleans only. */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        try {
            for (java.lang.reflect.RecordComponent c : Tunables.class.getRecordComponents()) {
                values.put(
                    c.getName(),
                    c.getAccessor()
                        .invoke(this));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return values;
    }

    /**
     * The values in {@code values}, by name; any missing or of the wrong type keep their default. Not checked: call
     * {@link #validate} on the result.
     */
    public static Tunables fromMap(java.util.Map<String, Object> values) {
        Tunables d = defaults();
        java.lang.reflect.RecordComponent[] components = Tunables.class.getRecordComponents();
        Object[] args = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        try {
            for (int i = 0; i < components.length; i++) {
                types[i] = components[i].getType();
                Object value = values.get(components[i].getName());
                Object fallback = components[i].getAccessor()
                    .invoke(d);
                args[i] = value != null && fallback.getClass()
                    .isInstance(value) ? value : fallback;
            }
            return Tunables.class.getDeclaredConstructor(types)
                .newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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
