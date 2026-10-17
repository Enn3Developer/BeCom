package com.enn3developer.becom.apps;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * What a phone's camera sees for the app on screen: the nearest block or creature the holder looks at, up to
 * {@link #RANGE} blocks away. Nothing behind a wall, and nothing the holder couldn't see themselves.
 */
final class PhoneCamera {

    static final double RANGE = 16;

    private PhoneCamera() {}

    /** A table for Lua: {@code kind} "block" or "entity", its name, where it is and how far; null for nothing. */
    static Map<String, Object> look(EntityPlayerMP holder) {
        Vec3 eye = Vec3.createVectorHelper(holder.posX, holder.posY + holder.getEyeHeight(), holder.posZ);
        Vec3 look = holder.getLook(1f);
        Vec3 end = eye.addVector(look.xCoord * RANGE, look.yCoord * RANGE, look.zCoord * RANGE);
        MovingObjectPosition hit = holder.worldObj.func_147447_a(eye, end, false, true, false);
        double blockDistance = hit == null ? RANGE : hit.hitVec.distanceTo(eye);
        Entity seen = null;
        double seenDistance = blockDistance;
        AxisAlignedBB around = holder.boundingBox
            .addCoord(look.xCoord * RANGE, look.yCoord * RANGE, look.zCoord * RANGE)
            .expand(1, 1, 1);
        for (Object o : (List<?>) holder.worldObj.getEntitiesWithinAABBExcludingEntity(holder, around)) {
            Entity e = (Entity) o;
            if (!e.canBeCollidedWith() || e.isInvisible()) continue;
            double grow = e.getCollisionBorderSize();
            MovingObjectPosition at = e.boundingBox.expand(grow, grow, grow)
                .calculateIntercept(eye, end);
            if (at == null) continue;
            double distance = at.hitVec.distanceTo(eye);
            if (distance < seenDistance) {
                seen = e;
                seenDistance = distance;
            }
        }
        Map<String, Object> out = new HashMap<>();
        if (seen != null) {
            out.put("kind", "entity");
            out.put("name", seen.getCommandSenderName());
            out.put("x", Math.floor(seen.posX));
            out.put("y", Math.floor(seen.posY));
            out.put("z", Math.floor(seen.posZ));
            out.put("distance", round(seenDistance));
            return out;
        }
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        Block block = holder.worldObj.getBlock(hit.blockX, hit.blockY, hit.blockZ);
        int meta = holder.worldObj.getBlockMetadata(hit.blockX, hit.blockY, hit.blockZ);
        out.put("kind", "block");
        out.put("name", name(block, meta));
        out.put("id", Block.blockRegistry.getNameForObject(block));
        out.put("meta", meta);
        out.put("x", hit.blockX);
        out.put("y", hit.blockY);
        out.put("z", hit.blockZ);
        out.put("distance", round(blockDistance));
        return out;
    }

    private static String name(Block block, int meta) {
        try {
            ItemStack stack = new ItemStack(block, 1, block.damageDropped(meta));
            if (stack.getItem() != null) return stack.getDisplayName();
        } catch (RuntimeException e) {
            // Some blocks have no item, or one that can't name itself off the client.
        }
        return block.getLocalizedName();
    }

    private static double round(double distance) {
        return Math.round(distance * 10) / 10.0;
    }
}
