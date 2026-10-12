package com.enn3developer.becom.logic.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
            a.addHistory(new PhoneRecord.HistoryEntry(i, 100_000 + i, "x", PhoneRecord.Direction.MISSED));
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
}
