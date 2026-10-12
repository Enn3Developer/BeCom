package com.enn3developer.becom.server;

import com.enn3developer.becom.logic.net.BlockPos;

/** A node anywhere in the world: its dimension and packed position. */
public record NodeRef(int dimension, long pos) {

    @Override
    public String toString() {
        return "[" + dimension + ": " + BlockPos.toString(pos) + "]";
    }
}
