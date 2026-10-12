package com.enn3developer.becom.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.net.BlockPos;
import com.enn3developer.becom.logic.net.NetNode;
import com.enn3developer.becom.logic.net.NetworkModel;
import com.enn3developer.becom.logic.net.NodeKind;
import com.enn3developer.becom.logic.phone.PhoneKind;
import com.enn3developer.becom.logic.phone.PhoneRecord;

/** DESIGN.md 14.2: saved data carries a format version, and data newer than BeCom understands is never overwritten. */
class SavedDataTest {

    private static NetworkSavedData networkWith(long a, long b, UUID owner) {
        NetworkSavedData data = new NetworkSavedData(NetworkSavedData.NAME);
        NetworkModel model = data.model();
        NetNode na = new NetNode(a, NodeKind.PYLON, owner);
        na.setHeight(12);
        na.setFormed(true);
        model.add(na);
        model.add(new NetNode(b, NodeKind.TERMINAL, null));
        model.link(a, b);
        model.setBlocked(a, b, true);
        return data;
    }

    @Test
    void networkRoundTripsWithItsLinksAndClearance() {
        long a = BlockPos.pack(-100, 70, 300);
        long b = BlockPos.pack(-90, 64, 300);
        UUID owner = UUID.randomUUID();
        NBTTagCompound nbt = new NBTTagCompound();
        networkWith(a, b, owner).writeToNBT(nbt);
        assertEquals(NetworkSavedData.FORMAT, nbt.getInteger(SavedDataFormat.KEY));

        NetworkSavedData read = new NetworkSavedData(NetworkSavedData.NAME);
        read.readFromNBT(nbt);
        NetNode na = read.model()
            .node(a);
        assertEquals(NodeKind.PYLON, na.kind());
        assertEquals(owner, na.owner());
        assertEquals(12, na.height());
        assertTrue(na.formed());
        assertTrue(
            read.model()
                .linked(a, b));
        assertTrue(
            read.model()
                .isBlocked(a, b));
        assertFalse(read.isDirty(), "freshly read data has nothing to save");
    }

    @Test
    void newerNetworkDataIsKeptAsIsAndNeverSaved() {
        NBTTagCompound nbt = new NBTTagCompound();
        networkWith(BlockPos.pack(0, 64, 0), BlockPos.pack(5, 64, 0), null).writeToNBT(nbt);
        nbt.setInteger(SavedDataFormat.KEY, NetworkSavedData.FORMAT + 1);
        nbt.setString("fromTheFuture", "keep me");

        NetworkSavedData read = new NetworkSavedData(NetworkSavedData.NAME);
        read.readFromNBT(nbt);
        assertTrue(read.isTooNew());
        assertEquals(
            0,
            read.model()
                .size(),
            "nothing from a newer format is interpreted");
        read.markDirty();
        assertFalse(read.isDirty(), "too-new data is never saved");
        NBTTagCompound written = new NBTTagCompound();
        read.writeToNBT(written);
        assertEquals(NetworkSavedData.FORMAT + 1, written.getInteger(SavedDataFormat.KEY));
        assertEquals("keep me", written.getString("fromTheFuture"));
    }

    @Test
    void dataFromBeforeVersionsCountsAsVersionOne() {
        assertEquals(1, SavedDataFormat.versionOf(new NBTTagCompound()));
        assertFalse(SavedDataFormat.tooNew(new NBTTagCompound(), 1));
    }

    @Test
    void savedLinksNeverExceedSlots() {
        long head = BlockPos.pack(0, 64, 0);
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagList nodes = new NBTTagList();
        nodes.appendTag(
            node(head, NodeKind.PYLON, BlockPos.pack(1, 64, 0), BlockPos.pack(2, 64, 0), BlockPos.pack(3, 64, 0)));
        for (int x = 1; x <= 3; x++) nodes.appendTag(node(BlockPos.pack(x, 64, 0), NodeKind.PYLON, head));
        nbt.setTag("nodes", nodes);
        NetworkSavedData read = new NetworkSavedData(NetworkSavedData.NAME);
        read.readFromNBT(nbt);
        assertEquals(
            2,
            read.model()
                .node(head)
                .links()
                .size());
    }

    private static NBTTagCompound node(long pos, NodeKind kind, long... links) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("pos", pos);
        tag.setString("kind", kind.name());
        tag.setBoolean("formed", true);
        tag.setInteger("height", 16);
        NBTTagList list = new NBTTagList();
        for (long link : links) {
            NBTTagCompound l = new NBTTagCompound();
            l.setLong("pos", link);
            list.appendTag(l);
        }
        tag.setTag("links", list);
        return tag;
    }

    @Test
    void unknownNodeKindsAreDroppedNotFatal() {
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagList nodes = new NBTTagList();
        NBTTagCompound bad = node(BlockPos.pack(0, 64, 0), NodeKind.PYLON);
        bad.setString("kind", "NOT_A_KIND");
        nodes.appendTag(bad);
        nbt.setTag("nodes", nodes);
        NetworkSavedData read = new NetworkSavedData(NetworkSavedData.NAME);
        read.readFromNBT(nbt);
        assertEquals(
            0,
            read.model()
                .size());
    }

    @Test
    void phoneRegistryRoundTripsSettingsBlockListAndHistory() {
        GlobalSavedData data = new GlobalSavedData(GlobalSavedData.NAME);
        UUID owner = UUID.randomUUID();
        UUID pest = UUID.randomUUID();
        PhoneRecord record = data.phones()
            .create(PhoneKind.TELEPHONE, owner, "alice", new Random(1));
        record.setLocation(-1, BlockPos.pack(10, 20, 30));
        record.setDoNotDisturb(true);
        record.setHidden(true);
        record.block(pest, "pest");
        record.addHistory(new PhoneRecord.HistoryEntry(1, 222_222, "bob", PhoneRecord.Direction.OUTGOING));
        record.addHistory(new PhoneRecord.HistoryEntry(2, 333_333, "carol", PhoneRecord.Direction.MISSED));
        NBTTagCompound nbt = new NBTTagCompound();
        data.writeToNBT(nbt);

        GlobalSavedData read = new GlobalSavedData(GlobalSavedData.NAME);
        read.readFromNBT(nbt);
        PhoneRecord back = read.phones()
            .get(record.number());
        assertEquals(owner, back.owner());
        assertEquals("alice", back.ownerName());
        assertEquals(-1, back.dimension());
        assertEquals(BlockPos.pack(10, 20, 30), back.pos());
        assertTrue(back.doNotDisturb());
        assertTrue(back.hidden());
        assertTrue(back.blocks(pest));
        assertEquals(
            2,
            back.history()
                .size());
        assertEquals(
            PhoneRecord.Direction.MISSED,
            back.history()
                .get(0)
                .direction());
        assertFalse(read.isDirty());
    }

    @Test
    void newerPhoneRegistryIsNeverOverwritten() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger(SavedDataFormat.KEY, GlobalSavedData.FORMAT + 5);
        GlobalSavedData read = new GlobalSavedData(GlobalSavedData.NAME);
        read.readFromNBT(nbt);
        assertTrue(read.isTooNew());
        read.markDirty();
        assertFalse(read.isDirty());
        assertNull(
            read.phones()
                .get(123_456));
    }
}
