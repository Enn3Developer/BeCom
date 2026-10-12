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

import com.enn3developer.becom.item.BeComItems;
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NodeRef;
import com.enn3developer.becom.logic.radio.AntennaNetwork;
import com.enn3developer.becom.logic.radio.RadioHops;

import micdoodle8.mods.galacticraft.api.prefab.entity.EntityAutoRocket;
import micdoodle8.mods.galacticraft.api.prefab.entity.EntitySpaceshipBase;
import micdoodle8.mods.galacticraft.api.world.IGalacticraftWorldProvider;

/**
 * Rocket Radio Receivers (DESIGN.md 9.3, 14, D28). Rockets are Galacticraft's entities, so BeCom can't tick them: it
 * keeps every rocket built on {@link EntityAutoRocket} as it joins its world, and once a second checks each one's pad,
 * launch phase and cargo. One standing unignited on a launch pad on a planet, with a receiver in its cargo, is a T1
 * antenna there, needing no power, and the planet's uplink to the Overworld's satellites. Server thread only.
 */
public final class RocketReceivers {

    private static final int TICKS_PER_SECOND = 20;
    // A receiver is an antenna of the first tier (DESIGN.md 9.3).
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

    /** An entity joined a world: a rocket is followed from now on (DESIGN.md 14). */
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
            if (counts(rocket)) now.put(refOf(rocket), rocket);
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
     * Whether a rocket's receiver counts (DESIGN.md 9.3): it stands unignited on a launch pad, on a planet (a
     * Galacticraft world other than the Overworld, X35), with a receiver in its cargo.
     */
    public static boolean counts(EntityAutoRocket rocket) {
        World world = rocket.worldObj;
        if (world.provider.dimensionId == 0 || !(world.provider instanceof IGalacticraftWorldProvider)) return false;
        if (rocket.launchPhase != EntitySpaceshipBase.EnumLaunchPhase.UNIGNITED.ordinal()) return false;
        if (rocket.getLandingPad() == null) return false;
        return carriesReceiver(rocket);
    }

    /** Whether a receiver is in the rocket's cargo; a rocket built without chests has none (DESIGN.md 9.3). */
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
        return new NodeRef(
            rocket.worldObj.provider.dimensionId,
            BlockPos.pack(
                MathHelper.floor_double(rocket.posX),
                MathHelper.floor_double(rocket.posY + rocket.height),
                MathHelper.floor_double(rocket.posZ)));
    }

    /**
     * Where the receiver transmits from (DESIGN.md 6.1): the top of the rocket, how far below the surface that is, and
     * the pad it stands on.
     */
    private static RadioHops.End endOf(EntityAutoRocket rocket) {
        double top = rocket.posY + rocket.height;
        int surface = rocket.worldObj
            .getHeightValue(MathHelper.floor_double(rocket.posX), MathHelper.floor_double(rocket.posZ));
        return new RadioHops.End(rocket.posX, top, rocket.posZ, surface - top, rocket.posY);
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
