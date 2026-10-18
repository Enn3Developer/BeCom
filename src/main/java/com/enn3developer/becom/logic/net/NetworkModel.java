package com.enn3developer.becom.logic.net;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Every node of one dimension's network. Server-thread only. {@link #version()} goes up on every change that can move
 * a call's path, so path finding reruns only when it has to.
 */
public final class NetworkModel {

    private final Map<Long, NetNode> nodes = new HashMap<>();
    private long version;

    public NetNode node(long pos) {
        return nodes.get(pos);
    }

    public Collection<NetNode> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public int size() {
        return nodes.size();
    }

    /** Adds {@code node}, replacing any node at the same position. */
    public void add(NetNode node) {
        nodes.put(node.pos(), node);
        changed();
    }

    /** Removes the node at {@code pos}. Returns it, or null when there was none. */
    public NetNode remove(long pos) {
        NetNode node = nodes.remove(pos);
        if (node != null) changed();
        return node;
    }

    public long version() {
        return version;
    }

    /** Marks a change the model can't see itself, such as a relay's partner. */
    public void changed() {
        version++;
    }

    /** An unordered pair of node positions. */
    public record LinkKey(long a, long b) {

        public static LinkKey of(long x, long y) {
            return x <= y ? new LinkKey(x, y) : new LinkKey(y, x);
        }

        public long other(long end) {
            return end == a ? b : a;
        }
    }
}
