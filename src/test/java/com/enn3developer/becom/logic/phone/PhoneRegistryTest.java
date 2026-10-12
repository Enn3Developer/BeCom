package com.enn3developer.becom.logic.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** D25 and DESIGN.md 12.3: contacts by owner name, hidden phones, block lists, do-not-disturb. */
class PhoneRegistryTest {

    private final PhoneRegistry registry = new PhoneRegistry();
    private final Random random = new Random(5);
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    @Test
    void contactsListReachablePhonesByOwnerName() {
        PhoneRecord mine = registry.create(PhoneKind.TELEPHONE, alice, "alice", random);
        PhoneRecord b = registry.create(PhoneKind.TELEPHONE, bob, "Bob", random);
        PhoneRecord c = registry.create(PhoneKind.TELEPHONE, carol, "carol", random);
        List<PhoneRegistry.Contact> contacts = registry
            .contacts(alice, mine.number(), Arrays.asList(mine.number(), c.number(), b.number()));
        assertEquals(2, contacts.size());
        assertEquals(
            "Bob",
            contacts.get(0)
                .ownerName());
        assertEquals(
            "carol",
            contacts.get(1)
                .ownerName());
    }

    @Test
    void hiddenPhonesAndPhonesThatBlockYouStayOutOfYourContacts() {
        PhoneRecord mine = registry.create(PhoneKind.TELEPHONE, alice, "alice", random);
        PhoneRecord b = registry.create(PhoneKind.TELEPHONE, bob, "bob", random);
        PhoneRecord c = registry.create(PhoneKind.TELEPHONE, carol, "carol", random);
        b.setHidden(true);
        c.block(alice, "alice");
        assertTrue(
            registry.contacts(alice, mine.number(), Arrays.asList(b.number(), c.number()))
                .isEmpty());
        // Someone else still sees carol, who only blocked alice.
        assertEquals(
            1,
            registry.contacts(bob, b.number(), Arrays.asList(c.number()))
                .size());
    }

    @Test
    void doNotDisturbAndBlockingRefuseCallsAndUnknownNumbersToo() {
        PhoneRecord b = registry.create(PhoneKind.TELEPHONE, bob, "bob", random);
        assertEquals(PhoneRegistry.Refusal.NONE, registry.refusal(alice, b.number()));
        b.setDoNotDisturb(true);
        assertEquals(PhoneRegistry.Refusal.DO_NOT_DISTURB, registry.refusal(alice, b.number()));
        b.setDoNotDisturb(false);
        b.block(alice, "alice");
        assertEquals(PhoneRegistry.Refusal.BLOCKED, registry.refusal(alice, b.number()));
        assertEquals(PhoneRegistry.Refusal.NONE, registry.refusal(carol, b.number()));
        b.unblock(alice);
        assertEquals(PhoneRegistry.Refusal.NONE, registry.refusal(alice, b.number()));
        assertEquals(PhoneRegistry.Refusal.NO_SUCH_NUMBER, registry.refusal(alice, 999_999));
    }

    @Test
    void numbersAreUniqueAndChangesMoveTheVersion() {
        long before = registry.version();
        PhoneRecord a = registry.create(PhoneKind.TELEPHONE, alice, "alice", random);
        PhoneRecord b = registry.create(PhoneKind.TELEPHONE, bob, "bob", random);
        assertNotEquals(a.number(), b.number());
        assertTrue(registry.version() > before);
        long after = registry.version();
        registry.remove(a.number());
        assertTrue(registry.version() > after);
        assertEquals(1, registry.size());
    }

    @Test
    void historyKeepsTheNewestEntriesOnly() {
        PhoneRecord a = registry.create(PhoneKind.TELEPHONE, alice, "alice", random);
        for (int i = 0; i < PhoneRecord.HISTORY_SIZE + 10; i++) {
            a.addHistory(new PhoneRecord.HistoryEntry(i, 100_000 + i, "x", PhoneRecord.Direction.MISSED, null));
        }
        assertEquals(
            PhoneRecord.HISTORY_SIZE,
            a.history()
                .size());
        assertEquals(
            PhoneRecord.HISTORY_SIZE + 9,
            a.history()
                .get(0)
                .time());
    }

    @Test
    void blockingARecentCallBlocksWhoeverCalledNotThePhonesOwner() {
        // Bob calls Alice from Carol's phone; Alice blocks that row of her recent calls.
        PhoneRecord mine = registry.create(PhoneKind.TELEPHONE, alice, "alice", random);
        PhoneRecord carols = registry.create(PhoneKind.TELEPHONE, carol, "carol", random);
        mine.addHistory(new PhoneRecord.HistoryEntry(1, carols.number(), "bob", PhoneRecord.Direction.MISSED, bob));
        PhoneRegistry.Blockable target = registry.recentTarget(mine, 1, carols.number());
        assertEquals(new PhoneRegistry.Blockable(bob, "bob"), target);
        mine.block(target.player(), target.name());
        assertEquals(PhoneRegistry.Refusal.BLOCKED, registry.refusal(bob, mine.number()));
        assertEquals(PhoneRegistry.Refusal.NONE, registry.refusal(carol, mine.number()));
    }

    @Test
    void aRowNamesWhoItWasAtTheTimeEvenIfTheNumberMovesOn() {
        // An unanswered call to Bob's phone records Bob, its owner then. The number is freed and reissued to Carol;
        // blocking the old row still blocks Bob.
        PhoneRecord mine = registry.create(PhoneKind.TELEPHONE, alice, "alice", random);
        PhoneRecord bobs = registry.create(PhoneKind.TELEPHONE, bob, "bob", random);
        int number = bobs.number();
        mine.addHistory(new PhoneRecord.HistoryEntry(1, number, "bob", PhoneRecord.Direction.OUTGOING, bob));
        registry.remove(number);
        registry.restore(new PhoneRecord(number, PhoneKind.TELEPHONE, carol, "carol"));
        assertEquals(new PhoneRegistry.Blockable(bob, "bob"), registry.recentTarget(mine, 1, number));
    }

    @Test
    void aRowWithNobodyRecordedBlocksNobody() {
        PhoneRecord mine = registry.create(PhoneKind.TELEPHONE, alice, "alice", random);
        PhoneRecord bobs = registry.create(PhoneKind.TELEPHONE, bob, "bob", random);
        mine.addHistory(new PhoneRecord.HistoryEntry(1, bobs.number(), "", PhoneRecord.Direction.OUTGOING, null));
        assertNull(registry.recentTarget(mine, 1, bobs.number()));
    }

    @Test
    void aRowIsFoundByItsTimeAndNumberNotItsPosition() {
        PhoneRecord mine = registry.create(PhoneKind.TELEPHONE, alice, "alice", random);
        mine.addHistory(new PhoneRecord.HistoryEntry(10, 222_222, "bob", PhoneRecord.Direction.MISSED, bob));
        mine.addHistory(new PhoneRecord.HistoryEntry(20, 222_222, "carol", PhoneRecord.Direction.MISSED, carol));
        // A new call lands on top between the screen's last view and the click; the row picked is still Bob's.
        mine.addHistory(new PhoneRecord.HistoryEntry(30, 333_333, "dan", PhoneRecord.Direction.MISSED, null));
        assertEquals(new PhoneRegistry.Blockable(bob, "bob"), registry.recentTarget(mine, 10, 222_222));
        assertNull(registry.recentTarget(mine, 10, 333_333));
        assertNull(registry.recentTarget(mine, 99, 222_222));
    }

    @Test
    void nobodyBlocksThemselves() {
        PhoneRecord mine = registry.create(PhoneKind.TELEPHONE, alice, "alice", random);
        mine.addHistory(new PhoneRecord.HistoryEntry(1, 222_222, "alice", PhoneRecord.Direction.MISSED, alice));
        assertNull(registry.recentTarget(mine, 1, 222_222));
    }

    @Test
    void onlyAShownContactCanBeBlocked() {
        PhoneRecord mine = registry.create(PhoneKind.TELEPHONE, alice, "alice", random);
        PhoneRecord bobs = registry.create(PhoneKind.TELEPHONE, bob, "bob", random);
        PhoneRecord carols = registry.create(PhoneKind.TELEPHONE, carol, "carol", random);
        carols.setHidden(true);
        List<Integer> reachable = Arrays.asList(bobs.number(), carols.number());
        assertEquals(
            new PhoneRegistry.Blockable(bob, "bob"),
            registry.contactTarget(alice, mine.number(), reachable, bobs.number()));
        // Carol is hidden, so blocking her number would tell a modified client who owns it.
        assertNull(registry.contactTarget(alice, mine.number(), reachable, carols.number()));
        // Nor any number outside the contacts at all.
        assertNull(registry.contactTarget(alice, mine.number(), List.of(bobs.number()), carols.number()));
    }

    @Test
    void theBlockListHasRoomFor128() {
        PhoneRecord mine = registry.create(PhoneKind.TELEPHONE, alice, "alice", random);
        for (int i = 0; i < PhoneRecord.MAX_BLOCKED; i++) assertTrue(mine.block(UUID.randomUUID(), "p" + i));
        assertFalse(mine.block(UUID.randomUUID(), "one more"));
        assertEquals(
            PhoneRecord.MAX_BLOCKED,
            mine.blocked()
                .size());
    }

    @Test
    void aHistoryLoadedLongerThanTheCapRollsAtThatLength() {
        // A newer BeCom kept 40; this one keeps 32 of its own, but doesn't cut what it was given.
        PhoneRecord mine = registry.create(PhoneKind.TELEPHONE, alice, "alice", random);
        for (int i = 0; i < PhoneRecord.HISTORY_SIZE + 8; i++) {
            mine.restoreHistory(
                new PhoneRecord.HistoryEntry(1000 - i, 222_222, "bob", PhoneRecord.Direction.MISSED, bob));
        }
        mine.addHistory(new PhoneRecord.HistoryEntry(2000, 333_333, "carol", PhoneRecord.Direction.INCOMING, carol));
        List<PhoneRecord.HistoryEntry> history = mine.history();
        assertEquals(PhoneRecord.HISTORY_SIZE + 8, history.size());
        assertEquals(
            2000,
            history.get(0)
                .time());
    }
}
