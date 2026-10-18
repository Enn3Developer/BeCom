package com.enn3developer.becom.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.enn3developer.becom.data.SatelliteSavedData;
import com.enn3developer.becom.logic.Tunables;
import com.enn3developer.becom.logic.radio.Band;
import com.enn3developer.becom.logic.radio.RadioModel;
import com.enn3developer.becom.logic.space.Satellites;
import com.enn3developer.becom.logic.space.SkyStrength;

/**
 * The satellites in orbit: launched from Launch Pads, each coming down after its lifetime, and the
 * sky hops they give every dimension with a sky and enough of them. Times are the Overworld's world time, which only
 * runs while the server does. Server thread only.
 */
public final class SpaceService {

    private static final int TICKS_PER_SECOND = 20;

    private final Tunables tunables;
    private final SkyStrength sky;
    private final Random random = new Random();
    // Dimensions' names for the Launch Pad's screen, which asks for them every tick it's open.
    private final Map<Integer, String> names = new HashMap<>();
    private Satellites satellites = new Satellites();
    private long ticks;

    public SpaceService(Tunables tunables) {
        this.tunables = tunables;
        this.sky = SkyStrength.of(tunables);
    }

    /** Takes the satellites saved with the world. */
    public void attach(SatelliteSavedData data) {
        satellites = data.satellites();
    }

    /**
     * A dimension's name for screens, as its world provider gives it, loaded or not. Asked once per dimension: a
     * provider made for a dimension that isn't loaded can cost a mod's setup.
     */
    public String dimensionName(int dimension) {
        return names.computeIfAbsent(dimension, SpaceService::nameOf);
    }

    private static String nameOf(int dimension) {
        WorldServer world = DimensionManager.getWorld(dimension);
        String name;
        try {
            name = world != null ? world.provider.getDimensionName()
                : DimensionManager.createProviderFor(dimension)
                    .getDimensionName();
        } catch (RuntimeException e) {
            name = null;
        }
        // A provider may leave its name out; the screen shows the number instead.
        return name == null ? String.valueOf(dimension) : name;
    }

    /** The Overworld's world time, the clock satellites go up and come down by. */
    public static long now() {
        WorldServer overworld = DimensionManager.getWorld(0);
        return overworld == null ? 0 : overworld.getTotalWorldTime();
    }

    /** One satellite goes up over {@code dimension}, to stay its lifetime, give or take the spread. */
    public void launch(int dimension) {
        long lifetime = Satellites
            .lifetime(tunables.satelliteLifetimeDays(), tunables.satelliteLifetimeSpread(), random.nextDouble());
        satellites.launch(dimension, now(), lifetime);
    }

    /**
     * Sets how many satellites orbit over {@code dimension}, for testing: the ones there come down,
     * and {@code count} fresh ones go up, each with its own lifetime.
     */
    public void set(int dimension, int count) {
        satellites.clear(dimension);
        for (int i = 0; i < count; i++) launch(dimension);
    }

    /** How many satellites orbit over {@code dimension}. */
    public int count(int dimension) {
        return satellites.count(dimension);
    }

    /** Every dimension with satellites over it, and how many. */
    public Map<Integer, Integer> counts() {
        return satellites.counts();
    }

    /** Ticks until the next satellite over {@code dimension} comes down, or -1 with none up. */
    public long untilNextDown(int dimension) {
        long next = satellites.nextDown(dimension);
        return next == Long.MAX_VALUE ? -1 : Math.max(0, next - now());
    }

    /** The ticks until each satellite over {@code dimension} comes down. */
    public List<Long> ticksLeft(int dimension) {
        long now = now();
        List<Long> left = new ArrayList<>();
        for (Satellites.Satellite satellite : satellites.all()) {
            if (satellite.dimension() == dimension) left.add(satellite.comesDown() - now);
        }
        return left;
    }

    /** Goes up whenever a satellite goes up or comes down, which moves sky hops. */
    public long version() {
        return satellites.version();
    }

    /** Whether {@code dimension} is loaded and has a sky; satellites don't reach one without. */
    public boolean hasSky(int dimension) {
        WorldServer world = DimensionManager.getWorld(dimension);
        return world != null && !world.provider.hasNoSky;
    }

    /** Whether {@code dimension} has a sky and satellites enough over it for sky hops. */
    public boolean skyHops(int dimension) {
        return hasSky(dimension) && sky.enough(satellites.count(dimension));
    }

    /**
     * The weather's loss on a sky hop in {@code dimension} now, in dB: it costs a hop whose ground end stands
     * where it rains or snows. 0 for a dimension that isn't loaded.
     */
    public double weather(int dimension) {
        WorldServer world = DimensionManager.getWorld(dimension);
        if (world == null) return 0;
        return tunables.weatherLoss(world.getRainStrength(1), world.getWeightedThunderStrength(1));
    }

    /**
     * A sky hop from a ground end in {@code dimension} {@code depth} blocks below the surface, on {@code band}, losing
     * {@code weather} dB; negative infinity where there are none.
     */
    public double hop(int dimension, double depth, Band band, double weather) {
        if (!skyHops(dimension)) return Double.NEGATIVE_INFINITY;
        return sky.hop(satellites.count(dimension), depth, band, weather);
    }

    /** The same hop term by term, for the Signal Analyzer; null where there are none. */
    public RadioModel.Losses losses(int dimension, double depth, Band band, double weather) {
        if (!hasSky(dimension)) return null;
        return sky.losses(satellites.count(dimension), depth, band, weather);
    }

    public SkyStrength sky() {
        return sky;
    }

    /** Once a second, satellites whose time has come fall out of orbit. */
    public void tick() {
        if (++ticks % TICKS_PER_SECOND == 0) satellites.decay(now());
    }
}
