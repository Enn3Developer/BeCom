package com.enn3developer.becom.block;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.enn3developer.becom.BeCom;
import com.enn3developer.becom.gui.PhoneScreen;
import com.enn3developer.becom.logic.phone.Cord;
import com.enn3developer.becom.server.BeComServer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiThemes;

/**
 * The Telephone's state (DESIGN.md 5.6): its number, whether it rings, and its phone screen. Who holds the handset
 * lives in the call service; the block's metadata carries only whether the handset is off the hook, for the model.
 */
public class TileTelephone extends TileEntity implements IGuiHolder<PosGuiData> {

    // A ring cycle: the ring sound plays for its first part, then silence, like a real bell (DESIGN.md 15.2).
    private static final int RING_PERIOD_TICKS = 120;
    // "You can move 1 block away" (DESIGN.md 5.6): the gap from the block to the player's body, with a little slack.
    private static final double CORD_REACH = 1.2;

    private int number = -1;
    private boolean ringing;
    private int ringTicks;
    private boolean hookChecked;

    public int number() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
        markDirty();
    }

    public boolean ringing() {
        return ringing;
    }

    public void setRinging(boolean ringing) {
        if (this.ringing == ringing) return;
        this.ringing = ringing;
        ringTicks = 0;
    }

    public boolean offHook() {
        return BlockTelephone.isOffHook(getBlockMetadata());
    }

    public void setOffHook(boolean offHook) {
        if (worldObj == null || offHook() == offHook) return;
        int meta = BlockTelephone.withOffHook(getBlockMetadata(), offHook);
        worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, meta, 3);
    }

    /**
     * Puts the handset back if nobody holds it. The off-hook bit is saved with the chunk, but who holds a handset
     * isn't, so after a restart or a crash a phone could otherwise show its handset missing with nobody holding it.
     * Runs on the first tick after the phone loads; the self-test calls it directly.
     */
    public void releaseStaleHandset() {
        if (!offHook()) return;
        BeComServer becom = BeComServer.get();
        if (becom == null || number < 0 || becom.calls.holderOf(number) == null) setOffHook(false);
    }

    /** Whether {@code player} is still within the handset's cord. */
    public boolean withinCord(EntityPlayer player) {
        if (worldObj == null || player.worldObj != worldObj || player.isDead) return false;
        // On the server, posY is the player's feet.
        return Cord.within(xCoord, yCoord, zCoord, player.posX, player.posY, player.posZ, CORD_REACH);
    }

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) return;
        if (!hookChecked) {
            hookChecked = true;
            releaseStaleHandset();
        }
        if (!ringing) return;
        if (ringTicks++ % RING_PERIOD_TICKS == 0) {
            worldObj.playSoundEffect(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, BeCom.MODID + ":phone.ring", 1.0f, 1.0f);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("number", number);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        number = nbt.hasKey("number") ? nbt.getInteger("number") : -1;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return PhoneScreen.build(this, data, syncManager);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(PosGuiData data, ModularPanel mainPanel) {
        return new ModularScreen(BeCom.MODID, mainPanel).useTheme(GTGuiThemes.STANDARD.getId());
    }
}
