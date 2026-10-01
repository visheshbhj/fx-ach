package com.fx.ach;

import com.afrunt.jach.domain.ACHRecord;
import com.fx.ach.core.AchCodes;
import com.fx.ach.core.AchService;
import com.fx.ach.core.FieldView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Presents one 94-character record as a labelled form: every field with its columns, its value
 * and what that value means.
 */
class RecordForm {

    /** Fields whose values come from a fixed code list get a drop-down instead of a text box. */
    private static final Map<String, Map<String, String>> CODE_LISTS = Map.of(
            "Transaction Code", AchCodes.TRANSACTION,
            "Service Class Code", AchCodes.SERVICE_CLASS,
            "Standard Entry Class Code", AchCodes.SEC,
            "Return Reason Code", AchCodes.RETURN_REASON,
            "Change Code", AchCodes.CHANGE_CODE);

    private final AchService service;
    private final ACHRecord record;
    private final String summary;
    private final BiConsumer<ACHRecord, Map<FieldView, String>> onApply;

    RecordForm(AchService service, ACHRecord record, String summary, BiConsumer<ACHRecord, Map<FieldView, String>> onApply) {
        this.service = service;
        this.record = record;
        this.summary = summary;
        this.onApply = onApply;
    }

    Node build() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));
        box.getStyleClass().add("record-form");

        Label title = new Label(AchService.recordTypeLabel(record) + "  ·  " + service.recordName(record));
        title.getStyleClass().add("h1");
        Label sub = new Label(summary + (record.getLineNumber() > 0 ? "   (line " + record.getLineNumber() + ")" : ""));
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);

        List<FieldView> fields = service.fields(record);
        GridPane grid = new GridPane();
        grid.getStyleClass().add("field-grid");
        grid.setHgap(14);
        grid.setVgap(6);
        ColumnConstraints c0 = new ColumnConstraints(200);
        ColumnConstraints c1 = new ColumnConstraints(160, 260, 420);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1, c2);

        Label h0 = header("Field");
        Label h1 = header("Value");
        Label h2 = header("Meaning");
        grid.addRow(0, h0, h1, h2);

        Map<FieldView, Supplier<String>> inputs = new LinkedHashMap<>();
        Map<FieldView, Control> controls = new LinkedHashMap<>();
        int row = 1;
        for (FieldView f : fields) {
            VBox name = new VBox(1);
            Label n = new Label(f.name());
            n.getStyleClass().add("field-name");
            Label pos = new Label("cols " + f.position() + " · " + f.length() + " char" + (f.length() == 1 ? "" : "s") + " · " + f.inclusion());
            pos.getStyleClass().add("field-pos");
            name.getChildren().addAll(n, pos);

            Control input = input(f, inputs);
            controls.put(f, input);

            Label meaning = new Label(f.meaning());
            meaning.setWrapText(true);
            meaning.getStyleClass().add("field-meaning");
            if (f.meaning().contains("INVALID") || f.meaning().startsWith("Unknown") || f.meaning().startsWith("Not a")) {
                meaning.getStyleClass().add("field-bad");
            }
            grid.addRow(row++, name, input, meaning);
        }

        Button apply = new Button("Apply Changes");
        apply.setDefaultButton(true);
        apply.getStyleClass().add("accent");
        apply.setOnAction(e -> {
            Map<FieldView, String> edits = new LinkedHashMap<>();
            inputs.forEach((f, value) -> {
                if (!value.get().trim().equals(f.value())) {
                    edits.put(f, value.get());
                }
            });
            onApply.accept(record, edits);
        });
        Button revert = new Button("Revert");
        revert.setOnAction(e -> onApply.accept(record, Map.of()));
        Label hint = new Label("Amounts are in dollars (e.g. 1250.00). Dates accept YYMMDD or YYYY-MM-DD. "
                + "Totals are recalculated automatically after edits to batches and entries.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        HBox buttons = new HBox(8, apply, revert);
        buttons.setAlignment(Pos.CENTER_LEFT);

        box.getChildren().addAll(title, sub, rawStrip(fields, controls), grid, buttons, hint);
        return box;
    }

    private Control input(FieldView f, Map<FieldView, Supplier<String>> inputs) {
        Map<String, String> codes = CODE_LISTS.get(f.name());
        if (f.editable() && codes != null) {
            ComboBox<String> combo = new ComboBox<>();
            codes.forEach((code, meaning) -> combo.getItems().add(code + " – " + meaning));
            String current = codes.containsKey(f.value()) ? f.value() + " – " + codes.get(f.value()) : f.value();
            if (!combo.getItems().contains(current)) {
                combo.getItems().add(0, current);
            }
            combo.setValue(current);
            combo.setMaxWidth(Double.MAX_VALUE);
            inputs.put(f, () -> {
                String v = combo.getValue() == null ? "" : combo.getValue();
                int dash = v.indexOf(" – ");
                return dash > 0 ? v.substring(0, dash) : v;
            });
            return combo;
        }
        TextField text = new TextField(f.value());
        text.getStyleClass().add("mono");
        text.setEditable(f.editable());
        if (!f.editable()) {
            text.getStyleClass().add("read-only");
            text.setTooltip(new Tooltip("Fixed by the NACHA format"));
        } else {
            int max = f.metadata().isBigDecimal() ? f.length() + 4 : f.length() + (f.metadata().isDate() ? 4 : 0);
            text.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().length() <= max ? c : null));
            inputs.put(f, text::getText);
        }
        return text;
    }

    /** The raw record with each field shaded alternately; hover for the field name, click to edit. */
    private Node rawStrip(List<FieldView> fields, Map<FieldView, Control> controls) {
        HBox strip = new HBox();
        strip.getStyleClass().add("raw-strip");
        int i = 0;
        for (FieldView f : fields) {
            Label seg = new Label(f.raw().replace(' ', '·'));
            seg.getStyleClass().addAll("seg", i++ % 2 == 0 ? "seg-a" : "seg-b");
            seg.setMinWidth(Control.USE_PREF_SIZE);
            Tooltip.install(seg, new Tooltip(f.name() + " (cols " + f.position() + ")\n" + f.meaning()));
            seg.setOnMouseClicked(e -> controls.get(f).requestFocus());
            strip.getChildren().add(seg);
        }
        Label caption = new Label("Raw record – hover a segment to see the field, click to jump to it (· = space)");
        caption.getStyleClass().add("muted");
        VBox wrap = new VBox(4, caption, strip);
        return wrap;
    }

    private static Label header(String text) {
        Label l = new Label(text.toUpperCase());
        l.getStyleClass().add("grid-header");
        return l;
    }
}
