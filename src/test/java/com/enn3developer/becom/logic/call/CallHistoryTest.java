package com.enn3developer.becom.logic.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.enn3developer.becom.logic.phone.PhoneRecord;

/** DESIGN.md 11.2: which recent-calls row each phone of a call keeps about the others. */
class CallHistoryTest {

    private static final int A = 111_111;
    private static final int B = 222_222;
    private static final int C = 333_333;

    @Test
    void aOneToOneCallIsOutgoingOneWayAndIncomingOrMissedTheOther() {
        Call.Leg caller = Call.Leg.caller(A, UUID.randomUUID(), 0);
        Call.Leg callee = Call.Leg.ringing(B, A, 0);
        assertEquals(PhoneRecord.Direction.OUTGOING, CallHistory.direction(caller, callee, false));
        assertEquals(PhoneRecord.Direction.MISSED, CallHistory.direction(callee, caller, false));
        callee.answer(UUID.randomUUID(), false);
        assertEquals(PhoneRecord.Direction.INCOMING, CallHistory.direction(callee, caller, true));
    }

    @Test
    void inAGroupPhonesThatTalkedRecordEachOtherAndOthersNothing() {
        // B added C: A and C only share a row if they were in the call at the same time.
        Call.Leg a = Call.Leg.caller(A, UUID.randomUUID(), 0);
        Call.Leg b = Call.Leg.ringing(B, A, 0);
        Call.Leg c = Call.Leg.ringing(C, B, 0);
        assertEquals(PhoneRecord.Direction.OUTGOING, CallHistory.direction(b, c, false));
        assertEquals(PhoneRecord.Direction.MISSED, CallHistory.direction(c, b, false));
        assertNull(CallHistory.direction(a, c, false));
        c.answer(UUID.randomUUID(), false);
        assertEquals(PhoneRecord.Direction.INCOMING, CallHistory.direction(a, c, true));
        assertEquals(PhoneRecord.Direction.INCOMING, CallHistory.direction(c, a, true));
    }
}
