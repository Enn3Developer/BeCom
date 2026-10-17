package com.enn3developer.becom.server;

import java.util.Map;
import java.util.WeakHashMap;

import appeng.api.networking.IGrid;

/**
 * Ids for the ME networks calls cross: the call graph sees each ME network as one vertex, named by
 * its grid while that grid lives. A grid AE2 merges or splits away is a new grid, and gets a new id. Server thread
 * only.
 */
public final class MeGrids {

    // Weak, so grids AE2 drops leave with it; a grid's identity is the object itself.
    private final Map<IGrid, Integer> ids = new WeakHashMap<>();
    private int next = 1;

    public int idOf(IGrid grid) {
        return ids.computeIfAbsent(grid, g -> next++);
    }
}
