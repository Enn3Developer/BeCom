package com.enn3developer.becom.gui.machine;

import java.util.ArrayList;
import java.util.List;

import com.enn3developer.becom.gui.paint.Canvas;
import com.enn3developer.becom.gui.paint.Paint;
import com.enn3developer.becom.logic.SignalLevel;
import com.enn3developer.becom.machine.InfoLines;

/**
 * A machine's readout on its dark display, from the machine boards (Machines, Gateways): the status on top, green or
 * red, with what it draws on its right; a row a fact, its label grey on the left; bars for calls and signal; and under
 * a line, each thing that's wrong, red, or that holds it back, orange.
 */
public final class Readout {

    private static final float ROW = 24;
    // Where values start: the boards' 96-pixel label column and its gap.
    private static final float VALUE = 104;
    private static final int TRACK = 0x2f3336;

    private Readout() {}

    private static SignalLevel level(double ordinal) {
        SignalLevel[] levels = SignalLevel.values();
        return levels[(int) Math.max(0, Math.min(levels.length - 1, ordinal))];
    }

    /** A value in a level's colour, orange where there's no level at all: something needs doing. */
    private static int valueColour(double ordinal) {
        SignalLevel level = level(ordinal);
        return level == SignalLevel.NONE ? Paint.ORANGE : Paint.level(level);
    }

    /** Paints {@code rows} from {@code y} in a column {@code w} wide; returns where it ended. */
    public static float paint(Canvas c, List<InfoLines.Row> rows, float x, float y, float w) {
        InfoLines.Row status = null;
        InfoLines.Row right = null;
        List<InfoLines.Row> problems = new ArrayList<>();
        for (InfoLines.Row row : rows) {
            if (row.kind() == InfoLines.Shape.STATUS && status == null) status = row;
            else if (row.kind() == InfoLines.Shape.RIGHT) right = row;
            else if (row.problem() && row.label()
                .isEmpty()) problems.add(row);
        }
        if (status != null || right != null) {
            float ty = y + (ROW - c.lineHeight(14)) / 2;
            if (status != null && status.fraction() >= 1) {
                // A hop's status: its bars, in its level's colour.
                SignalLevel level = level(status.fraction() - 1);
                Paint.bars(c, x, y + (ROW - 12) / 2, level);
                c.text(status.text(), x + 26, ty, 14, Paint.level(level));
            } else if (status != null) {
                int colour = status.problem() ? Paint.RED : status.fraction() < 0 ? Paint.ORANGE : Paint.GREEN;
                c.rect(x, y + (ROW - 8) / 2, 8, 8, colour);
                c.text(status.text(), x + 16, ty, 14, colour);
            }
            if (right != null) Paint.right(c, right.text(), x + w, ty, 14, Paint.LIGHT);
            y += ROW + 4;
        }
        float valueX = x + VALUE;
        float valueW = w - VALUE;
        for (int i = 0; i < rows.size(); i++) {
            InfoLines.Row row = rows.get(i);
            if (row == status || row == right || problems.contains(row) || row.kind() == InfoLines.Shape.SUB) continue;
            List<String> subs = new ArrayList<>();
            for (int j = i + 1; j < rows.size() && rows.get(j)
                .kind() == InfoLines.Shape.SUB; j++) subs.add(
                    rows.get(j)
                        .text());
            float ty = y + (ROW - c.lineHeight(14)) / 2;
            if (row.kind() == InfoLines.Shape.SECTION) {
                c.text(row.text(), x, y + 4, 12, Paint.DARK);
                y += c.lineHeight(12) + 8;
                c.rect(x, y - 3, w, 1, 0x3a3a3a);
                continue;
            }
            if (row.kind() == InfoLines.Shape.BAR && row.label()
                .isEmpty()) {
                // A bar the width of the display, as a launch's progress.
                double f = Math.max(0, Math.min(1, row.fraction()));
                c.rect(x, y, w, 8, TRACK);
                c.rect(x, y, (float) (w * f), 8, Paint.GREEN);
                y += 16;
                continue;
            }
            if (row.label()
                .isEmpty()) {
                for (String line : Paint.wrap(c, row.text(), 13, w)) {
                    c.text(line, x, y + (ROW - c.lineHeight(13)) / 2, 13, Paint.TEXT);
                    y += ROW - 6;
                }
                y += 6;
                continue;
            }
            c.text(Paint.fit(c, row.label(), 14, VALUE - 8), x, ty, 14, Paint.GREY);
            switch (row.kind()) {
                case InfoLines.Shape.BAR -> {
                    float tw = c.width(row.text(), 14);
                    float bw = valueW - tw - 14;
                    c.rect(valueX, y + (ROW - 8) / 2, bw, 8, TRACK);
                    double f = Math.max(0, Math.min(1, row.fraction()));
                    c.rect(valueX, y + (ROW - 8) / 2, (float) (bw * f), 8, f >= 1 ? Paint.ORANGE : Paint.GREEN);
                    Paint.right(c, row.text(), x + w, ty, 14, Paint.TEXT);
                    y += ROW;
                }
                case InfoLines.Shape.LEVEL -> {
                    SignalLevel level = SignalLevel
                        .values()[(int) Math.max(0, Math.min(SignalLevel.values().length - 1, row.fraction()))];
                    Paint.bars(c, valueX, y + (ROW - 12) / 2, level);
                    c.text(row.text(), valueX + 26, ty, 14, Paint.level(level));
                    y += ROW;
                }
                default -> {
                    int colour = row.problem() ? Paint.RED
                        : row.kind() == InfoLines.Shape.GOOD ? Paint.GREEN
                            : row.kind() == InfoLines.Shape.VALUE ? valueColour(row.fraction()) : Paint.TEXT;
                    // A long value wraps under itself, as the boards' do.
                    List<String> lines = Paint.wrap(c, row.text(), 14, valueW);
                    String value = lines.isEmpty() ? "" : lines.get(0);
                    for (int l = 1; l < lines.size(); l++) {
                        c.text(lines.get(l), valueX, ty + l * (ROW - 6), 14, colour);
                    }
                    c.text(value, valueX, ty, 14, colour);
                    float after = lines.size() > 1 ? x + w : valueX + c.width(value, 14) + 8;
                    y += ROW + (lines.size() - 1) * (ROW - 6);
                    // Grey words after the value, wrapping in the room left on its right, or on a row of their own
                    // under it when there's little room.
                    for (String sub : subs) {
                        float room = x + w - after;
                        if (room < 120) {
                            after = valueX;
                            room = valueW;
                            ty = y + (ROW - c.lineHeight(14)) / 2;
                            y += ROW;
                        }
                        List<String> parts = Paint.wrap(c, sub, 12, room);
                        for (int l = 0; l < parts.size(); l++) {
                            c.text(parts.get(l), after, ty + 2 + l * (ROW - 8), 12, Paint.GREY);
                        }
                        if (parts.size() > 1) y += (parts.size() - 1) * (ROW - 8);
                        after = parts.size() > 1 ? x + w : after + c.width(sub, 12) + 8;
                    }
                }
            }
        }
        if (!problems.isEmpty()) {
            y += 4;
            c.rect(x, y, w, 1, 0x3a3a3a);
            y += 8;
            for (InfoLines.Row problem : problems) {
                int colour = problem.kind() == InfoLines.Shape.WARN ? Paint.ORANGE : Paint.RED;
                List<String> lines = Paint.wrap(c, problem.text(), 14, w - 16);
                c.rect(x, y + (ROW - 8) / 2, 8, 8, colour);
                for (String line : lines) {
                    c.text(line, x + 16, y + (ROW - c.lineHeight(14)) / 2, 14, colour);
                    y += ROW - 4;
                }
                y += 4;
            }
        }
        return y;
    }
}
