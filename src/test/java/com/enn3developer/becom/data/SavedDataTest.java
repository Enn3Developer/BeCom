package com.enn3developer.becom.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void anUnknownNodeKindMeansANewerBeComSavedIt() {
        // DESIGN.md 14.2: data a newer BeCom wrote is kept untouched and never saved over, not partly dropped.
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagList nodes = new NBTTagList();
        nodes.appendTag(node(BlockPos.pack(0, 64, 0), NodeKind.PYLON));
        NBTTagCompound future = node(BlockPos.pack(10, 64, 0), NodeKind.PYLON);
        future.setString("kind", "SATELLITE_DISH_MK2");
        nodes.appendTag(future);
        nbt.setTag("nodes", nodes);
        NetworkSavedData read = new NetworkSavedData(NetworkSavedData.NAME);
        read.readFromNBT(nbt);
        assertTrue(read.isTooNew());
        assertFalse(read.isDirty());
        NBTTagCompound written = new NBTTagCompound();
        read.writeToNBT(written);
        assertEquals(nbt, written);
    }

    @Test
    void anUnknownPhoneKindMeansANewerBeComSavedIt() {
        GlobalSavedData data = new GlobalSavedData(GlobalSavedData.NAME);
        data.phones()
            .create(PhoneKind.TELEPHONE, UUID.randomUUID(), "alice", new Random(1));
        NBTTagCompound nbt = new NBTTagCompound();
        data.writeToNBT(nbt);
        NBTTagCompound future = (NBTTagCompound) nbt.getTagList("phones", 10)
            .getCompoundTagAt(0)
            .copy();
        future.setInteger("number", 654_321);
        future.setString("kind", "HOLOPHONE");
        nbt.getTagList("phones", 10)
            .appendTag(future);
        GlobalSavedData read = new GlobalSavedData(GlobalSavedData.NAME);
        read.readFromNBT(nbt);
        assertTrue(read.isTooNew());
        assertFalse(read.isDirty());
        assertEquals(
            0,
            read.phones()
                .size());
    }

    @Test
    void dataVanillaFailedToReadIsUnreadable() {
        // MapStorage swallows a read error and hands back an object readFromNBT never ran on.
        NetworkSavedData failed = new NetworkSavedData(NetworkSavedData.NAME);
        assertFalse(failed.wasRead());
        assertTrue(SavedDataFormat.unreadable(true, failed.wasRead()));
        NetworkSavedData read = new NetworkSavedData(NetworkSavedData.NAME);
        read.readFromNBT(new NBTTagCompound());
        assertFalse(SavedDataFormat.unreadable(true, read.wasRead()));
        // No file at all is a new world, not a broken one.
        assertFalse(SavedDataFormat.unreadable(false, false));
        assertFalse(new GlobalSavedData(GlobalSavedData.NAME).wasRead());
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
        record.setSilent(true);
        record.block(pest, "pest");
        UUID carol = UUID.randomUUID();
        record.addHistory(new PhoneRecord.HistoryEntry(1, 222_222, "bob", PhoneRecord.Direction.OUTGOING, null));
        record.addHistory(new PhoneRecord.HistoryEntry(2, 333_333, "carol", PhoneRecord.Direction.MISSED, carol));
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
        assertTrue(back.silent());
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
        assertEquals(
            carol,
            back.history()
                .get(0)
                .otherPlayer());
        assertEquals(
            null,
            back.history()
                .get(1)
                .otherPlayer());
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

    @Test
    void aFileCutShortStopsTheLoadInsteadOfStartingEmpty(@TempDir File dir) throws Exception {
        // Written whole, then cut short, as a crash mid-save would leave it.
        StorageFixture fixture = new StorageFixture(dir);
        NetworkSavedData data = new NetworkSavedData(NetworkSavedData.NAME);
        data.model()
            .add(new NetNode(BlockPos.pack(1, 64, 1), NodeKind.PYLON, null));
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound inner = new NBTTagCompound();
        data.writeToNBT(inner);
        root.setTag("data", inner);
        File file = fixture.file(NetworkSavedData.NAME);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            CompressedStreamTools.writeCompressed(root, out);
        }
        assertEquals(
            data.model()
                .size(),
            NetworkSavedData.load(fixture.storage(), file, "test")
                .model()
                .size());
        // Cut short inside the compressed stream. (Cut later, the NBT reader fails by building a crash report, which
        // needs FML and can't run in a unit test; on a server it throws, and MapStorage swallows it just the same.)
        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
        java.nio.file.Files.write(file.toPath(), java.util.Arrays.copyOf(bytes, 12));
        IllegalStateException e = assertThrows(
            IllegalStateException.class,
            () -> NetworkSavedData.load(fixture.storage(), file, "test"));
        assertTrue(
            e.getMessage()
                .contains("couldn't read"),
            e.getMessage());
    }

    @Test
    void noFileIsANewWorld(@TempDir File dir) {
        StorageFixture fixture = new StorageFixture(dir);
        File file = fixture.file(GlobalSavedData.NAME);
        net.minecraft.world.storage.MapStorage storage = fixture.storage();
        GlobalSavedData data = GlobalSavedData.load(storage, file);
        assertEquals(
            0,
            data.phones()
                .size());
        // A second load from the same storage, after the first save wrote the file, isn't mistaken for an unreadable
        // one.
        data.phones()
            .create(PhoneKind.TELEPHONE, UUID.randomUUID(), "alice", new Random(1));
        storage.saveAllData();
        assertTrue(file.isFile());
        assertTrue(GlobalSavedData.load(storage, file) == data);
    }

    @Test
    void aBlockListLongerThanThisBeComAllowsLoadsWhole() {
        GlobalSavedData data = new GlobalSavedData(GlobalSavedData.NAME);
        PhoneRecord record = data.phones()
            .create(PhoneKind.TELEPHONE, UUID.randomUUID(), "alice", new Random(1));
        NBTTagCompound nbt = new NBTTagCompound();
        data.writeToNBT(nbt);
        NBTTagCompound phone = nbt.getTagList("phones", 10)
            .getCompoundTagAt(0);
        NBTTagList blocked = new NBTTagList();
        for (int i = 0; i < PhoneRecord.MAX_BLOCKED + 20; i++) {
            NBTTagCompound entry = new NBTTagCompound();
            UUID id = UUID.randomUUID();
            entry.setLong("playerMost", id.getMostSignificantBits());
            entry.setLong("playerLeast", id.getLeastSignificantBits());
            entry.setString("name", "p" + i);
            blocked.appendTag(entry);
        }
        phone.setTag("blocked", blocked);
        GlobalSavedData read = new GlobalSavedData(GlobalSavedData.NAME);
        read.readFromNBT(nbt);
        assertEquals(
            PhoneRecord.MAX_BLOCKED + 20,
            read.phones()
                .get(record.number())
                .blocked()
                .size());
    }

    @Test
    void anUnknownHistoryDirectionMeansANewerBeComSavedIt() {
        GlobalSavedData data = new GlobalSavedData(GlobalSavedData.NAME);
        PhoneRecord record = data.phones()
            .create(PhoneKind.TELEPHONE, UUID.randomUUID(), "alice", new Random(1));
        record.addHistory(new PhoneRecord.HistoryEntry(1, 222_222, "bob", PhoneRecord.Direction.MISSED, null));
        NBTTagCompound nbt = new NBTTagCompound();
        data.writeToNBT(nbt);
        nbt.getTagList("phones", 10)
            .getCompoundTagAt(0)
            .getTagList("history", 10)
            .getCompoundTagAt(0)
            .setString("dir", "REJECTED");
        GlobalSavedData read = new GlobalSavedData(GlobalSavedData.NAME);
        read.readFromNBT(nbt);
        assertTrue(read.isTooNew());
        assertFalse(read.isDirty());
    }

    @Test
    void moreLinksThanAKindHasSlotsMeansANewerBeComSavedIt() {
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagList nodes = new NBTTagList();
        long hub = BlockPos.pack(0, 64, 0);
        NBTTagCompound pylon = node(hub, NodeKind.PYLON);
        NBTTagList links = new NBTTagList();
        for (int i = 1; i <= 3; i++) {
            long other = BlockPos.pack(10 * i, 64, 0);
            nodes.appendTag(node(other, NodeKind.PYLON));
            NBTTagCompound link = new NBTTagCompound();
            link.setLong("pos", other);
            links.appendTag(link);
        }
        pylon.setTag("links", links);
        nodes.appendTag(pylon);
        nbt.setTag("nodes", nodes);
        NetworkSavedData read = new NetworkSavedData(NetworkSavedData.NAME);
        read.readFromNBT(nbt);
        assertTrue(read.isTooNew());
        assertFalse(read.isDirty());
    }

    @Test
    void aHistoryLongerThanThisBeComKeepsLoadsWhole() {
        GlobalSavedData data = new GlobalSavedData(GlobalSavedData.NAME);
        PhoneRecord record = data.phones()
            .create(PhoneKind.TELEPHONE, UUID.randomUUID(), "alice", new Random(1));
        NBTTagCompound nbt = new NBTTagCompound();
        data.writeToNBT(nbt);
        NBTTagList history = new NBTTagList();
        for (int i = 0; i < PhoneRecord.HISTORY_SIZE + 8; i++) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("time", 1000 - i);
            entry.setInteger("number", 222_222);
            entry.setString("name", "bob");
            entry.setString("dir", "MISSED");
            history.appendTag(entry);
        }
        nbt.getTagList("phones", 10)
            .getCompoundTagAt(0)
            .setTag("history", history);
        GlobalSavedData read = new GlobalSavedData(GlobalSavedData.NAME);
        read.readFromNBT(nbt);
        List<PhoneRecord.HistoryEntry> back = read.phones()
            .get(record.number())
            .history();
        assertEquals(PhoneRecord.HISTORY_SIZE + 8, back.size());
        // Newest first, as saved.
        assertEquals(
            1000,
            back.get(0)
                .time());
    }

    @Test
    void anInvalidOrRepeatedNumberStopsTheLoad() {
        GlobalSavedData data = new GlobalSavedData(GlobalSavedData.NAME);
        data.phones()
            .create(PhoneKind.TELEPHONE, UUID.randomUUID(), "alice", new Random(1));
        NBTTagCompound nbt = new NBTTagCompound();
        data.writeToNBT(nbt);
        NBTTagCompound twin = (NBTTagCompound) nbt.getTagList("phones", 10)
            .getCompoundTagAt(0)
            .copy();
        nbt.getTagList("phones", 10)
            .appendTag(twin);
        GlobalSavedData read = new GlobalSavedData(GlobalSavedData.NAME);
        read.readFromNBT(nbt);
        assertTrue(read.isTooNew());

        NBTTagCompound bad = new NBTTagCompound();
        data.writeToNBT(bad);
        bad.getTagList("phones", 10)
            .getCompoundTagAt(0)
            .setInteger("number", 12);
        GlobalSavedData badRead = new GlobalSavedData(GlobalSavedData.NAME);
        badRead.readFromNBT(bad);
        assertTrue(badRead.isTooNew());
    }

    @Test
    void aRepeatedNumberIsReportedAsDamageNotAsANewerBeCom(@TempDir File dir) throws Exception {
        GlobalSavedData data = new GlobalSavedData(GlobalSavedData.NAME);
        data.phones()
            .create(PhoneKind.TELEPHONE, UUID.randomUUID(), "alice", new Random(1));
        NBTTagCompound inner = new NBTTagCompound();
        data.writeToNBT(inner);
        inner.getTagList("phones", 10)
            .appendTag(
                inner.getTagList("phones", 10)
                    .getCompoundTagAt(0)
                    .copy());
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("data", inner);
        StorageFixture fixture = new StorageFixture(dir);
        File file = fixture.file(GlobalSavedData.NAME);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            CompressedStreamTools.writeCompressed(root, out);
        }
        IllegalStateException e = assertThrows(
            IllegalStateException.class,
            () -> GlobalSavedData.load(fixture.storage(), file));
        assertTrue(
            e.getMessage()
                .contains("damaged"),
            e.getMessage());
        assertFalse(
            e.getMessage()
                .contains("newer BeCom"),
            e.getMessage());
    }
}
