package com.enn3developer.becom.logic.call;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntFunction;

import com.enn3developer.becom.logic.phone.PhoneKind;

/**
 * Who a call's quest tasks complete for as it connects or a phone joins it (DESIGN.md 15.5): First call for everyone
 * in it, Mobile call for everyone in it on a Mobile Phone, and ME Bridge call for each ME Phone's user whose path to
 * another phone of the call goes through an ME Bridge. Pure, so it's tested without a world.
 */
public final class CallQuests {

    /** The players each task completes for; empty lists when it completes for nobody. */
    public record Done(List<UUID> firstCall, List<UUID> mobileCall, List<UUID> meBridgeCall) {

        public static final Done NONE = new Done(List.of(), List.of(), List.of());
    }

    /** Whether the best path from one phone to another goes through an ME Bridge. */
    @FunctionalInterface
    public interface Bridged {

        boolean between(int from, int to);
    }

    private CallQuests() {}

    /** @param kinds each phone's kind, null when it has none */
    public static Done of(Call call, IntFunction<PhoneKind> kinds, Bridged bridged) {
        if (call.state() != Call.State.CONNECTED) return Done.NONE;
        List<UUID> mobile = new ArrayList<>();
        List<UUID> viaBridge = new ArrayList<>();
        List<Call.Leg> active = call.activeLegs();
        for (Call.Leg leg : active) {
            PhoneKind kind = kinds.apply(leg.phone());
            if (kind == PhoneKind.MOBILE) mobile.addAll(leg.players());
            if (kind != PhoneKind.ME_PHONE) continue;
            for (Call.Leg other : active) {
                if (other != leg && bridged.between(leg.phone(), other.phone())) {
                    viaBridge.addAll(leg.players());
                    break;
                }
            }
        }
        return new Done(call.participants(), mobile, viaBridge);
    }
}
