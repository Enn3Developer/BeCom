package com.enn3developer.becom.logic.net;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One node of a dimension's network, as saved (DESIGN.md 14). Mutable and server-thread only; the routing thread
 * never sees it.
 */
public final class NetNode {

    private final long pos;
    private final NodeKind kind;
    private UUID owner;
    private int height;
    private boolean formed;
    private NodeRef partner;
    private final Set<Long> links = new LinkedHashSet<>();
    private final Set<Long> linksView = Collections.unmodifiableSet(links);

    public NetNode(long pos, NodeKind kind, UUID owner) {
        this.pos = pos;
        this.kind = kind;
        this.owner = owner;
    }

    public long pos() {
        return pos;
    }

    public int x() {
        return BlockPos.x(pos);
    }

    public int y() {
        return BlockPos.y(pos);
    }

    public int z() {
        return BlockPos.z(pos);
    }

    public NodeKind kind() {
        return kind;
    }

    /** The player who placed it; null when unknown (placed by a machine). */
    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    /** A pylon head's frame column height; 0 for nodes on the ground. */
    public int height() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    /** Whether its structure is complete. Nodes without a structure count as formed. */
    public boolean formed() {
        return formed;
    }

    public void setFormed(boolean formed) {
        this.formed = formed;
    }

    /** A Portal Relay's partner on the far side of its portal (DESIGN.md 9.4), or null. */
    public NodeRef partner() {
        return partner;
    }

    /** Sets or clears the partner; the caller marks the model changed. */
    public void setPartner(NodeRef partner) {
        this.partner = partner;
    }

    public Set<Long> links() {
        return linksView;
    }

    public boolean hasFreeSlot() {
        return links.size() < kind.linkSlots();
    }

    boolean addLink(long other) {
        return links.add(other);
    }

    boolean removeLink(long other) {
        return links.remove(other);
    }

    public double distanceTo(NetNode other) {
        return BlockPos.distance(pos, other.pos);
    }
}
