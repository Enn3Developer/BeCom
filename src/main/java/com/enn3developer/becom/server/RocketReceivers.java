package com.enn3developer.becom.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.item.BeComItems;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
import com.enn3developer.becom.logic.radio.RadioHops;

import micdoodle8.mods.galacticraft.api.prefab.entity.EntityAutoRocket;
import micdoodle8.mods.galacticraft.api.prefab.entity.EntitySpaceshipBase;
import micdoodle8.mods.galacticraft.api.world.IGalacticraftWorldProvider;

/**
 * Rocket Radio Receivers. Rockets are Galacticraft's entities, so BeCom can't tick them: it
 * keeps every rocket built on {@link EntityAutoRocket} as it joins its world, and once a second checks each one's pad,
 * launch phase and cargo. One standing unignited on a launch pad on a planet, with a receiver in its cargo, is a T1
 * antenna there, needing no power, and the planet's uplink to the Overworld's satellites. Server thread only.
 */
public final class RocketReceivers {

    private static final int TICKS_PER_SECOND = 20;
    // A receiver is an antenna of the first tier.
    private static final int TIER = 1;
    // Galacticraft keeps a rocket's last two slots for itself; the rest is cargo.
    private static final int OWN_SLOTS = 2;

    private final NetworkService network;
    private final AntennaNetwork antennas;
    // Rockets seen joining a world. Weak, so a rocket its world dropped never stays here.
    private final Set<EntityAutoRocket> rockets = Collections.newSetFromMap(new WeakHashMap<>());
    // The receivers that counted at the last look, by where their rockets stand.
    private Map<NodeRef, EntityAutoRocket> counting = new HashMap<>();
    private long ticks;

    public RocketReceivers(NetworkService network, AntennaNetwork antennas) {
        this.network = network;
        this.antennas = antennas;
    }

    /** An entity joined a world: a rocket is followed from now on. */
    public void joined(Entity entity) {
        if (entity instanceof EntityAutoRocket rocket && !entity.worldObj.isRemote) rockets.add(rocket);
    }

    public void tick() {
        if (++ticks % TICKS_PER_SECOND == 0) look();
    }

    /** Looks at every rocket followed: which receivers count now, and which stopped. */
    void look() {
        Map<NodeRef, EntityAutoRocket> now = new HashMap<>();
        Iterator<EntityAutoRocket> it = rockets.iterator();
        while (it.hasNext()) {
            EntityAutoRocket rocket = it.next();
            if (!inWorld(rocket)) {
                it.remove();
                continue;
            }
            try {
                if (counts(rocket)) now.put(refOf(rocket), rocket);
            } catch (RuntimeException e) {
                // Another mod's rocket that fails to say where it stands or what it carries: BeCom stops following
                // it rather than take the server down with it.
                BeCom.LOG.warn(
                    "Not following rocket {} any more: it threw when asked for its pad or cargo",
                    rocket.getClass()
                        .getName(),
                    e);
                it.remove();
            }
        }
        for (NodeRef gone : counting.keySet()) {
            if (now.containsKey(gone)) continue;
            network.receiverGone(gone.dimension(), gone.pos());
            antennas.gone(gone);
        }
        for (Map.Entry<NodeRef, EntityAutoRocket> entry : now.entrySet()) {
            EntityAutoRocket rocket = entry.getValue();
            network.receiver(
                rocket.worldObj,
                entry.getKey()
                    .pos());
            antennas.seen(entry.getKey(), TIER, endOf(rocket), false, null);
        }
        counting = now;
    }

    /** Whether a rocket is still in its world: not dead, its world loaded, and that world still holding it. */
    private static boolean inWorld(EntityAutoRocket rocket) {
        if (rocket.isDead || !(rocket.worldObj instanceof WorldServer world)) return false;
        return DimensionManager.getWorld(world.provider.dimensionId) == world
            && world.getEntityByID(rocket.getEntityId()) == rocket;
    }

    /**
     * Whether a rocket's receiver counts: it stands unignited on a launch pad, on a planet (a
     * Galacticraft world other than the Overworld), with a receiver in its cargo.
     */
    public static boolean counts(EntityAutoRocket rocket) {
        World world = rocket.worldObj;
        if (world.provider.dimensionId == 0 || !(world.provider instanceof IGalacticraftWorldProvider)) return false;
        if (rocket.launchPhase != EntitySpaceshipBase.EnumLaunchPhase.UNIGNITED.ordinal()) return false;
        if (rocket.getLandingPad() == null) return false;
        return carriesReceiver(rocket);
    }

    /** Whether a receiver is in the rocket's cargo; a rocket built without chests has none. */
    public static boolean carriesReceiver(EntityAutoRocket rocket) {
        int cargo = rocket.getSizeInventory() - OWN_SLOTS;
        for (int slot = 0; slot < cargo; slot++) {
            ItemStack stack = rocket.getStackInSlot(slot);
            if (stack != null && stack.getItem() == BeComItems.rocketRadioReceiver) return true;
        }
        return false;
    }

    /** Where a receiver counts from: the block at the top of its rocket. */
    private static NodeRef refOf(EntityAutoRocket rocket) {
        return new NodeRef(rocket.worldObj.provider.dimensionId, BlockPos.pack(x(rocket), top(rocket), z(rocket)));
    }

    /**
     * Where the receiver transmits from: the top of the rocket's top block, how far below the surface
     * that is, and the pad it stands on. Whole blocks, like an antenna's head, so the rocket's position settling by a
     * hair doesn't make a new antenna that surveys again.
     */
    private static RadioHops.End endOf(EntityAutoRocket rocket) {
        int x = x(rocket), z = z(rocket);
        double eye = top(rocket) + 1;
        int surface = rocket.worldObj.getHeightValue(x, z);
        return new RadioHops.End(x + 0.5, eye, z + 0.5, surface - eye, MathHelper.floor_double(rocket.posY));
    }

    private static int x(EntityAutoRocket rocket) {
        return MathHelper.floor_double(rocket.posX);
    }

    private static int z(EntityAutoRocket rocket) {
        return MathHelper.floor_double(rocket.posZ);
    }

    private static int top(EntityAutoRocket rocket) {
        return MathHelper.floor_double(rocket.posY + rocket.height);
    }

    /** Whether BeCom follows {@code rocket}; for the self-test. */
    public boolean followsForSelfTest(EntityAutoRocket rocket) {
        return rockets.contains(rocket);
    }

    /** How many receivers count now; for the self-test. */
    public int countingForSelfTest() {
        return counting.size();
    }

    /** Whether a receiver counts at {@code ref}; for the self-test. */
    public boolean countsForSelfTest(NodeRef ref) {
        return counting.containsKey(ref);
    }

    /** Looks at every rocket now, rather than at the next whole second; for the self-test. */
    public void lookForSelfTest() {
        look();
    }

    public void stop() {
        rockets.clear();
        counting.clear();
    }
}
