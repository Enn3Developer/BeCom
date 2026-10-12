package com.enn3developer.becom.packet;

import java.io.IOException;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;

import com.enn3developer.becom.BeCom;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * What a person in a video call holds and wears (DESIGN.md 10.1: the front camera shows their held item and armor),
 * sent to those who see them when it changes: the held item first, then boots, leggings, chestplate and helmet.
 */
public final class VideoEquipmentPacket implements IMessage {

    public static final int SLOTS = 5;
    // Tags past this are left off: the picture still shows the item, maybe without its looks. GT's tools keep their
    // material in a tag well under this.
    private static final int MAX_TAG_BYTES = 4096;

    private UUID source;
    private ItemStack[] stacks = new ItemStack[SLOTS];

    public VideoEquipmentPacket() {}

    public VideoEquipmentPacket(UUID source, ItemStack[] stacks) {
        this.source = source;
        this.stacks = stacks;
    }

    public UUID source() {
        return source;
    }

    public ItemStack[] stacks() {
        return stacks;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        Wire.writeUuid(buf, source);
        for (int i = 0; i < SLOTS; i++) ByteBufUtils.writeItemStack(buf, slim(i < stacks.length ? stacks[i] : null));
    }

    private static ItemStack slim(ItemStack stack) {
        if (stack == null || stack.getTagCompound() == null) return stack;
        try {
            if (CompressedStreamTools.compress(stack.getTagCompound()).length <= MAX_TAG_BYTES) return stack;
        } catch (IOException e) {
            // Unwritable here means unwritable in the packet too: send it bare.
        }
        ItemStack bare = stack.copy();
        bare.setTagCompound(null);
        return bare;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        source = Wire.readUuid(buf);
        stacks = new ItemStack[SLOTS];
        for (int i = 0; i < SLOTS; i++) stacks[i] = ByteBufUtils.readItemStack(buf);
    }

    public static final class Handler implements IMessageHandler<VideoEquipmentPacket, IMessage> {

        @Override
        public IMessage onMessage(VideoEquipmentPacket message, MessageContext ctx) {
            BeCom.proxy.onVideoEquipment(message);
            return null;
        }
    }
}
