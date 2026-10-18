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
import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.phone.Cord;
import com.enn3developer.becom.logic.phone.PhoneRecord;
import com.enn3developer.becom.machine.InfoLines;
import com.enn3developer.becom.machine.WailaInfo;
import com.enn3developer.becom.server.BeComServer;
import com.enn3developer.becom.server.PhoneHandle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.modularui2.GTGuiThemes;

/**
 * The Telephone's state: its number, whether it rings, and its phone screen. Who holds the handset
 * lives in the call service; the block's metadata carries only whether the handset is off the hook, for the model.
 */
public class TileTelephone extends TileEntity implements IGuiHolder<PosGuiData> {

    // A ring cycle: the ring sound plays for its first part, then silence, like a real bell. Carried
    // phones ring the same.
    public static final int RING_PERIOD_TICKS = 120;
    // "You can move 1 block away": the gap from the block to the player's body, with a little slack.
    private static final double CORD_REACH = 1.2;

    private int number = -1;
    private boolean ringing;
    private int ringTicks;
    private long settledAt = Long.MIN_VALUE;
    private int settledIn;

    public int number() {
        return number;
    }

    public void setNumber(int number) {
        // A new number isn't ringing, whatever the old one was doing.
        if (number != this.number) setRinging(false);
        this.number = number;
        markDirty();
        // Its bus lists it by number.
        TileCable.busChanged(worldObj, xCoord, yCoord, zCoord);
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
     * What a Telephone does on its first tick after loading: claims its number again if it was moved or lets go of it
     * if it's a copy, puts back a handset nobody holds, and rings if a call is ringing it.
     */
    public void settleAfterLoad() {
        BeComServer becom = BeComServer.get();
        PhoneRecord original = becom == null || number < 0 ? null : becom.phones.record(number);
        if (original != null && !becom.phones.relocate(this)) {
            // A copy: its number is the original's, which still stands elsewhere. Holding it would put the number on
            // two cables and ring both. It gets its own, owned by the original's owner.
            setNumber(
                becom.phones.registerCopy(worldObj, BlockPos.pack(xCoord, yCoord, zCoord), original)
                    .number());
        }
        releaseStaleHandset();
        restoreRinging();
    }

    /**
     * Puts the handset back if nobody holds it. The off-hook bit is saved with the chunk, but who holds a handset
     * isn't, so after a restart or a crash a phone could otherwise show its handset missing with nobody holding it.
     * Runs on the first tick after the phone loads
     */
    public void releaseStaleHandset() {
        if (!offHook()) return;
        BeComServer becom = BeComServer.get();
        if (becom == null || number < 0 || becom.calls.holderOf(number) == null) setOffHook(false);
    }

    /**
     * Rings again if a call is ringing this phone: whether it rings isn't saved with the chunk, so a phone whose chunk
     * reloads mid-ring would otherwise stay silent. Runs on the first tick after the phone loads.
     */
    public void restoreRinging() {
        BeComServer becom = BeComServer.get();
        // Only the phone the number belongs to: the call stops ringing that one, and no other.
        if (becom != null && number >= 0 && becom.phones.telephone(number) == this && becom.calls.isRinging(number))
            setRinging(true);
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
        // On the first tick after loading, and again if a mover carried this same tile object somewhere else.
        long here = BlockPos.pack(xCoord, yCoord, zCoord);
        int dimension = worldObj.provider.dimensionId;
        if (here != settledAt || dimension != settledIn) {
            settledAt = here;
            settledIn = dimension;
            settleAfterLoad();
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
        // Format 1 is the first; the number is all a Telephone keeps.
        number = nbt.hasKey("number") ? nbt.getInteger("number") : -1;
    }

    private final WailaInfo waila = new WailaInfo(this::info);

    /** Its lines for WAILA. */
    public WailaInfo waila() {
        return waila;
    }

    /** Its lines for WAILA, as on the Scanner board: ringing or not, and its number. */
    private InfoLines info() {
        InfoLines info = new InfoLines();
        BeComServer becom = BeComServer.get();
        if (ringing()) info.add("becom.info.ringing")
            .statusBusy();
        else if (offHook()) info.add("becom.info.off_hook")
            .statusBusy();
        else info.add("becom.info.state_idle")
            .status();
        if (number >= 0) info.add("becom.info.value", number)
            .row("becom.info.row.number");
        if (becom != null && worldObj != null)
            InfoLines.buses(info, becom, worldObj.provider.dimensionId, BlockPos.pack(xCoord, yCoord, zCoord));
        return info;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return PhoneScreen.build(new PhoneHandle.Telephone(this), data.getPlayer(), syncManager);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public ModularScreen createScreen(PosGuiData data, ModularPanel mainPanel) {
        return new ModularScreen(BeCom.MODID, mainPanel).useTheme(GTGuiThemes.STANDARD.getId());
    }
}
