package com.enn3developer.becom.logic.net;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Every node and link of one dimension's network (DESIGN.md 14), and which links a clearance check found blocked
 * (D20). Server-thread only. {@link #version()} goes up on every change that can move a call's path, so path finding
 * reruns only when it has to.
 */
public final class NetworkModel {

    private final Map<Long, NetNode> nodes = new HashMap<>();
    private final Set<LinkKey> blocked = new HashSet<>();
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

    /** Adds {@code node}, replacing any node at the same position along with that node's links. */
    public void add(NetNode node) {
        remove(node.pos());
        nodes.put(node.pos(), node);
        changed();
    }

    /** Removes the node at {@code pos} and every link to it. Returns it, or null when there was none. */
    public NetNode remove(long pos) {
        NetNode node = nodes.remove(pos);
        if (node == null) return null;
        for (long other : node.links()
            .toArray(new Long[0])) {
            NetNode neighbour = nodes.get(other);
            if (neighbour != null) neighbour.removeLink(pos);
            blocked.remove(LinkKey.of(pos, other));
            node.removeLink(other);
        }
        changed();
        return node;
    }

    /** Links two nodes both ways. No rules are checked here; see {@link LinkRules}. */
    public boolean link(long a, long b) {
        NetNode na = nodes.get(a);
        NetNode nb = nodes.get(b);
        if (na == null || nb == null || a == b) return false;
        boolean added = na.addLink(b) | nb.addLink(a);
        if (added) changed();
        return added;
    }

    public boolean unlink(long a, long b) {
        NetNode na = nodes.get(a);
        NetNode nb = nodes.get(b);
        boolean removed = false;
        if (na != null) removed |= na.removeLink(b);
        if (nb != null) removed |= nb.removeLink(a);
        blocked.remove(LinkKey.of(a, b));
        if (removed) changed();
        return removed;
    }

    public boolean linked(long a, long b) {
        NetNode na = nodes.get(a);
        return na != null && na.links()
            .contains(b);
    }

    public boolean isBlocked(long a, long b) {
        return blocked.contains(LinkKey.of(a, b));
    }

    /** Records a clearance result; true when it changed the link's state. */
    public boolean setBlocked(long a, long b, boolean isBlocked) {
        LinkKey key = LinkKey.of(a, b);
        boolean changedState = isBlocked ? blocked.add(key) : blocked.remove(key);
        if (changedState) changed();
        return changedState;
    }

    public Set<LinkKey> blockedLinks() {
        return Collections.unmodifiableSet(blocked);
    }

    /** Every link once, as an unordered pair. */
    public Set<LinkKey> links() {
        Set<LinkKey> all = new HashSet<>();
        for (NetNode node : nodes.values()) {
            for (long other : node.links()) all.add(LinkKey.of(node.pos(), other));
        }
        return all;
    }

    public long version() {
        return version;
    }

    /** Marks a change the model can't see itself: a node's formed state, height or power. */
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
