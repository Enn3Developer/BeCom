package com.enn3developer.becom.logic.net;

import java.util.UUID;

/**
 * One node of a dimension's network, as saved. Mutable and server-thread only; the routing thread
 * never sees it.
 */
public final class NetNode {

    private final long pos;
    private final NodeKind kind;
    private UUID owner;
    private NodeRef partner;

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

    /** A Portal Relay's partner on the far side of its portal, or null. */
    public NodeRef partner() {
        return partner;
    }

    /** Sets or clears the partner; the caller marks the model changed. */
    public void setPartner(NodeRef partner) {
        this.partner = partner;
    }
}
