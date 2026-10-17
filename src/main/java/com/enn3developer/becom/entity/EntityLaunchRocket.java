package com.enn3developer.becom.entity;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * The Satellite Rocket a Launch Pad shows: it stands in the pad's flame pit while a launch counts down,
 * then lifts off when the launch ends and climbs out of sight. Only a sight: it carries nothing, nothing hits it, and
 * it's never saved, since the pad stands a new one whenever a launch is under way.
 * <p>
 * Server and client fly it the same way from the tick it lifts off, so the client draws a smooth climb of its own
 * instead of following the server's position updates.
 */
public class EntityLaunchRocket extends Entity {

    private static final int FLYING = 20;
    // It climbs this much faster each tick, up to its top speed, in blocks a tick, and is gone this high above the pad.
    private static final double ACCELERATION = 0.02;
    private static final double TOP_SPEED = 2.0;
    private static final double CEILING = 200;
    // Drawn from this far away, in blocks: a launch is meant to be seen.
    private static final double SEEN_FROM = 256;

    private double liftOffY = Double.NaN;

    public EntityLaunchRocket(World world) {
        super(world);
        setSize(2.0f, 5.0f);
        noClip = true;
        ignoreFrustumCheck = true;
    }

    public EntityLaunchRocket(World world, double x, double y, double z) {
        this(world);
        setPosition(x, y, z);
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObject(FLYING, (byte) 0);
    }

    public boolean flying() {
        return dataWatcher.getWatchableObjectByte(FLYING) != 0;
    }

    /** The countdown is over: up it goes. */
    public void liftOff() {
        dataWatcher.updateObject(FLYING, (byte) 1);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!flying()) {
            if (worldObj.isRemote && rand.nextInt(4) == 0) vent();
            return;
        }
        if (Double.isNaN(liftOffY)) liftOffY = posY;
        motionY = Math.min(TOP_SPEED, motionY + ACCELERATION);
        setPosition(posX, posY + motionY, posZ);
        if (worldObj.isRemote) {
            exhaust();
        } else if (posY - liftOffY > CEILING) {
            setDead();
        }
    }

    /** Fuel boiling off while it waits, from halfway up. */
    private void vent() {
        double side = rand.nextBoolean() ? 0.6 : -0.6;
        worldObj.spawnParticle("cloud", posX + side, posY + 2.0, posZ + (rand.nextDouble() - 0.5), side * 0.1, 0, 0);
    }

    /** Flame from the engine and smoke billowing out of the pit. */
    private void exhaust() {
        for (int i = 0; i < 6; i++) {
            double dx = (rand.nextDouble() - 0.5) * 0.6;
            double dz = (rand.nextDouble() - 0.5) * 0.6;
            worldObj.spawnParticle("flame", posX + dx, posY - 0.2, posZ + dz, dx * 0.2, -0.6, dz * 0.2);
            worldObj.spawnParticle("largesmoke", posX + dx * 3, posY - 0.5, posZ + dz * 3, dx * 0.5, -0.1, dz * 0.5);
        }
        if (posY - liftOffY < 8) {
            for (int i = 0; i < 4; i++) {
                double angle = rand.nextDouble() * Math.PI * 2;
                worldObj.spawnParticle(
                    "cloud",
                    posX,
                    liftOffY + 0.3,
                    posZ,
                    Math.cos(angle) * 0.4,
                    0.05,
                    Math.sin(angle) * 0.4);
            }
        }
    }

    @Override
    public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int increments) {
        // In flight the client flies it itself; the server's coarser updates would only make it stutter.
        if (!flying()) super.setPositionAndRotation2(x, y, z, yaw, pitch, increments);
    }

    @Override
    public boolean isInRangeToRenderDist(double distanceSquared) {
        return distanceSquared < SEEN_FROM * SEEN_FROM;
    }

    @Override
    public AxisAlignedBB getBoundingBox() {
        return null;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean writeToNBTOptional(NBTTagCompound tag) {
        // Never saved: the pad stands a new one for a launch still under way when its chunk loads again.
        return false;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {}

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {}
}
