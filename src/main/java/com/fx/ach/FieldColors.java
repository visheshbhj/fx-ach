package com.fx.ach;

import com.fx.ach.core.FieldView;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * One colour per field position, shared by the raw view and the record form so a field looks the
 * same wherever it appears.
 */
final class FieldColors {

    private static final String[] PALETTE = {
            "#dbe7ff", "#fde4c4", "#d4f0dd", "#f7d9ec", "#e4ddfb", "#cfeef3", "#fbdcd2", "#ecefc4"
    };

    private FieldColors() {
    }

    static String color(int fieldIndex) {
        return PALETTE[fieldIndex % PALETTE.length];
    }

    /** A coloured span of a raw record for one field, with a tooltip naming the field. */
    static Label segment(FieldView f, int index) {
        Label seg = new Label(f.raw());
        seg.getStyleClass().add("seg");
        seg.setStyle("-fx-background-color: " + color(index) + ";");
        seg.setMinWidth(Region.USE_PREF_SIZE);
        Tooltip tip = new Tooltip(tooltip(f));
        tip.setShowDelay(Duration.millis(150));
        tip.setShowDuration(Duration.seconds(30));
        Tooltip.install(seg, tip);
        return seg;
    }

    static String tooltip(FieldView f) {
        String raw = f.raw().isBlank() ? "(blank)" : "\"" + f.raw() + "\"";
        return f.name() + "   ·   cols " + f.position() + " (" + f.length() + ")"
                + "\nValue: " + raw
                + (f.meaning().isBlank() ? "" : "\nMeaning: " + f.meaning())
                + "\n" + f.inclusion() + (f.editable() ? "" : " · fixed by format");
    }

    /** Small colour chip shown next to a field name in forms. */
    static Region swatch(int index) {
        Region r = new Region();
        r.getStyleClass().add("swatch");
        r.setStyle("-fx-background-color: " + color(index) + ";");
        return r;
    }
}
