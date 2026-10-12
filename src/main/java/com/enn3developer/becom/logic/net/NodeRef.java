package com.enn3developer.becom.logic.net;

/** A node anywhere in the world: its dimension and packed position. */
public record NodeRef(int dimension, long pos) {

    @Override
    public String toString() {
        return "[" + dimension + ": " + BlockPos.toString(pos) + "]";
    }
}
