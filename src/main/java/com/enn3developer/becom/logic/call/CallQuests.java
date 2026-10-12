package com.enn3developer.becom.logic.call;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;

import com.enn3developer.becom.logic.phone.PhoneKind;

/**
 * Who a call's quest tasks complete for as it connects or a phone joins it (DESIGN.md 15.5): First call for everyone
 * in it, Mobile call for everyone in it on a Mobile Phone, ME Bridge call for each ME Phone's user whose path to
 * another phone of the call goes through an ME Bridge, and Call between dimensions for everyone in it when its people
 * are in different dimensions. Pure, so it's tested without a world.
 */
public final class CallQuests {

    /** The players each task completes for; empty lists when it completes for nobody. */
    public record Done(List<UUID> firstCall, List<UUID> mobileCall, List<UUID> meBridgeCall,
        List<UUID> betweenDimensions) {

        public static final Done NONE = new Done(List.of(), List.of(), List.of(), List.of());
    }

    /** Whether the best path from one phone to another goes through an ME Bridge. */
    @FunctionalInterface
    public interface Bridged {

        boolean between(int from, int to);
    }

    private CallQuests() {}

    /**
     * @param kinds       each phone's kind, null when it has none
     * @param dimensionOf the dimension each player is in, null when they aren't online
     */
    public static Done of(Call call, IntFunction<PhoneKind> kinds, Bridged bridged,
        Function<UUID, Integer> dimensionOf) {
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
        List<UUID> everyone = call.participants();
        return new Done(everyone, mobile, viaBridge, spansDimensions(everyone, dimensionOf) ? everyone : List.of());
    }

    /** Whether {@code players} are in more than one dimension, by those online. */
    public static boolean spansDimensions(List<UUID> players, Function<UUID, Integer> dimensionOf) {
        Set<Integer> dimensions = new HashSet<>();
        for (UUID player : players) {
            Integer dimension = dimensionOf.apply(player);
            if (dimension != null) dimensions.add(dimension);
        }
        return dimensions.size() > 1;
    }
}
