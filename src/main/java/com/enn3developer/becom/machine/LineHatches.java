package com.enn3developer.becom.machine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.StatCollector;

import com.enn3developer.becom.logic.net.BlockPos;

import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.IGTHatchAdder;

/**
 * A multiblock's Line Hatches, as its structure checks find them. A check clears them and the structure adds each one
 * it finds ({@link Element}); once the check is done, the hatches it found join the controller if it formed, and the
 * rest leave it. A hatch joining or leaving has the buses at it worked out again; one that stays is left alone, so a
 * check every few seconds costs the buses nothing.
 */
public final class LineHatches {

    /** The Line Hatch, for a structure's hatch adder. */
    public enum Element implements IHatchElement<LineHatchHost> {

        LINE_HATCH;

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return Collections.singletonList(MTEHatchLine.class);
        }

        @Override
        public IGTHatchAdder<? super LineHatchHost> adder() {
            return (host, tile, casing) -> host.lineHatches()
                .add(tile, casing == null ? 0 : casing);
        }

        @Override
        public long count(LineHatchHost host) {
            return host.lineHatches()
                .count();
        }

        @Override
        public String getDisplayName() {
            return StatCollector.translateToLocal(getDescriptionLangKey());
        }

        @Override
        public String getDescriptionLangKey() {
            return "becom.hatch.line";
        }
    }

    private final List<MTEHatchLine> found = new ArrayList<>();
    private final List<MTEHatchLine> joined = new ArrayList<>();

    /** Before a structure check. */
    public void clear() {
        found.clear();
    }

    /** GT's hatch adder: a Line Hatch where the structure takes one, in the structure's casing. */
    public boolean add(IGregTechTileEntity tile, int casingIndex) {
        if (tile == null || !(tile.getMetaTileEntity() instanceof MTEHatchLine hatch)) return false;
        hatch.updateTexture(casingIndex);
        if (!found.contains(hatch)) found.add(hatch);
        return true;
    }

    /** How many the last structure check found so far. */
    public int count() {
        return found.size();
    }

    /** The hatches joined to the controller now. */
    public List<MTEHatchLine> hatches() {
        return Collections.unmodifiableList(joined);
    }

    /** After a structure check: the hatches it found join the controller if it formed, and the rest leave it. */
    public void finished(IGregTechTileEntity controller, boolean formed) {
        long pos = pos(controller);
        List<MTEHatchLine> now = formed ? new ArrayList<>(found) : List.of();
        for (MTEHatchLine hatch : joined) if (!now.contains(hatch)) hatch.leave(pos);
        for (MTEHatchLine hatch : now) if (!joined.contains(hatch)) hatch.join(pos);
        joined.clear();
        joined.addAll(now);
    }

    /** The controller is broken: every hatch leaves it. */
    public void dropped(IGregTechTileEntity controller) {
        long pos = pos(controller);
        for (MTEHatchLine hatch : joined) hatch.leave(pos);
        joined.clear();
        found.clear();
    }

    private static long pos(IGregTechTileEntity tile) {
        return BlockPos.pack(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
    }
}
