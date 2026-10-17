package com.enn3developer.becom.logic.call;

import com.enn3developer.becom.logic.phone.PhoneRecord;

/**
 * Which row a call leaves in a phone's recent calls about another phone of the same call. A phone
 * records the phones it rang and the ones that rang it, as outgoing, incoming or missed, and in a group call also
 * every other phone it was in the call with at the same time, as incoming. Nothing for a phone it never talked with
 * and had no part in ringing.
 */
public final class CallHistory {

    private CallHistory() {}

    /**
     * The row {@code own} records about {@code other}, or null for none.
     *
     * @param talked whether the two were both in the call at some moment
     */
    public static PhoneRecord.Direction direction(Call.Leg own, Call.Leg other, boolean talked) {
        if (other.invitedBy() == own.phone()) return PhoneRecord.Direction.OUTGOING;
        if (own.invitedBy() == other.phone()) {
            return own.answered() ? PhoneRecord.Direction.INCOMING : PhoneRecord.Direction.MISSED;
        }
        return talked ? PhoneRecord.Direction.INCOMING : null;
    }
}
