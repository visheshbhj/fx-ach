package com.fx.ach;

import com.afrunt.jach.domain.ACHRecord;
import com.fx.ach.core.AchCodes;
import com.fx.ach.core.AchService;
import com.fx.ach.core.FieldView;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The right-hand vertical form for the selected record. Tracks unsaved edits so the caller can
 * ask before they are lost.
 */
class RecordPanel extends BorderPane {

    enum RawAction {
        REPLACE("Replace this record"), BEFORE("Insert before"), AFTER("Insert after");

        final String label;

        RawAction(String label) {
            this.label = label;
        }
    }

    interface RawHandler {
        void handle(RawAction action, ACHRecord record, String text);
    }

    /** Fields whose values come from a fixed code list get a drop-down instead of a text box. */
    private static final Map<String, Map<String, String>> CODE_LISTS = Map.of(
            "Transaction Code", AchCodes.TRANSACTION,
            "Service Class Code", AchCodes.SERVICE_CLASS,
            "Standard Entry Class Code", AchCodes.SEC,
            "Return Reason Code", AchCodes.RETURN_REASON,
            "Change Code", AchCodes.CHANGE_CODE);

    private final AchService service;
    private final BiConsumer<ACHRecord, Map<FieldView, String>> onApply;
    private final RawHandler onRaw;
    private final TextArea rawArea = new TextArea();
    private final Label rawInfo = new Label();
    private final TitledPane rawPane;
    private final BooleanProperty dirty = new SimpleBooleanProperty();
    private final Map<FieldView, Supplier<String>> inputs = new LinkedHashMap<>();
    private final Map<Integer, Control> controlsByStart = new LinkedHashMap<>();
    private final VBox fieldsBox = new VBox(10);
    private final ScrollPane scroll = new ScrollPane(fieldsBox);
    private final Label title = new Label();
    private final Label subtitle = new Label();
    private final Label modeHint = new Label();
    private ACHRecord record;

    RecordPanel(AchService service, BiConsumer<ACHRecord, Map<FieldView, String>> onApply, RawHandler onRaw) {
        this.service = service;
        this.onApply = onApply;
        this.onRaw = onRaw;
        this.rawPane = rawSection();
        getStyleClass().add("record-panel");

        title.getStyleClass().add("h2");
        title.setWrapText(true);
        subtitle.getStyleClass().add("muted");
        subtitle.setWrapText(true);
        modeHint.getStyleClass().add("muted");
        modeHint.setWrapText(true);

        Button apply = new Button("Apply");
        apply.getStyleClass().add("accent");
        apply.disableProperty().bind(dirty.not());
        apply.setOnAction(e -> apply());
        Button revert = new Button("Revert");
        revert.disableProperty().bind(dirty.not());
        revert.setOnAction(e -> show(record, subtitle.getText()));
        Label unsaved = new Label("● unsaved changes");
        unsaved.getStyleClass().add("unsaved");
        unsaved.visibleProperty().bind(dirty);
        HBox buttons = new HBox(8, apply, revert, unsaved);
        buttons.setAlignment(Pos.CENTER_LEFT);

        Label caption = new Label("RECORD");
        caption.getStyleClass().add("pane-title-inline");
        VBox head = new VBox(4, caption, title, subtitle, buttons, modeHint);
        head.setPadding(new Insets(8, 12, 8, 12));
        head.getStyleClass().add("record-panel-head");
        setTop(head);

        fieldsBox.setPadding(new Insets(10, 12, 16, 12));
        scroll.setFitToWidth(true);
        setCenter(scroll);
        setEditMode(false);
        show(null, "");
    }

    /**
     * Paste or edit raw 94-character line(s): replace the current record with them, or insert them
     * before/after it. Several lines may be pasted at once (e.g. a whole batch).
     */
    private TitledPane rawSection() {
        rawArea.getStyleClass().add("mono");
        rawArea.setPrefRowCount(2);
        rawArea.setWrapText(false);
        rawArea.setPromptText("Paste one or more 94-character records here");
        rawArea.textProperty().addListener(o -> rawInfo.setText(rawSummary(rawArea.getText())));
        rawInfo.getStyleClass().add("muted");
        rawInfo.setWrapText(true);
        HBox buttons = new HBox(6);
        for (RawAction action : RawAction.values()) {
            Button b = new Button(action.label);
            b.setOnAction(e -> {
                if (record != null) {
                    onRaw.handle(action, record, rawArea.getText());
                }
            });
            buttons.getChildren().add(b);
        }
        Label hint = new Label("Edit the line above to update this record, or paste new line(s) and insert them. "
                + "Short lines are padded; CR/LF is ignored.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        TitledPane pane = new TitledPane("Raw line – paste to update or insert", new VBox(6, rawArea, rawInfo, buttons, hint));
        pane.setAnimated(false);
        pane.getStyleClass().add("raw-section");
        return pane;
    }

    /** "1 line · 94 chars · Entry Detail" style feedback for the raw box. */
    static String rawSummary(String text) {
        List<String> lines = text.lines().map(l -> l.replace("\r", "")).filter(l -> !l.isBlank()).toList();
        if (lines.isEmpty()) {
            return "Empty";
        }
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            String type = AchCodes.RECORD_TYPE.getOrDefault(l.substring(0, 1), "✖ unknown record type");
            String len = l.length() == AchService.RECORD_LENGTH ? "94 chars ✔"
                    : l.length() < AchService.RECORD_LENGTH ? l.length() + " chars (will be padded)"
                    : l.stripTrailing().length() <= AchService.RECORD_LENGTH ? "94 chars + trailing spaces"
                    : "✖ " + l.length() + " chars – too long";
            sb.append(type).append(" · ").append(len);
        }
        return lines.size() + " line(s):\n" + sb;
    }

    void setEditMode(boolean on) {
        modeHint.setText(on
                ? "Edit mode: changes are applied automatically when you move to another record."
                : "Changes are kept until you press Apply or Enter; you'll be asked before they're lost.");
    }

    ACHRecord record() {
        return record;
    }

    boolean isDirty() {
        return dirty.get();
    }

    Map<FieldView, String> edits() {
        Map<FieldView, String> edits = new LinkedHashMap<>();
        inputs.forEach((f, value) -> {
            if (!value.get().trim().equals(f.value())) {
                edits.put(f, value.get());
            }
        });
        return edits;
    }

    void apply() {
        if (record != null) {
            onApply.accept(record, edits());
        }
    }

    void focusField(int start) {
        Control c = controlsByStart.get(start);
        if (c != null) {
            c.requestFocus();
            // bring the field into view
            double y = c.localToScene(0, 0).getY() - fieldsBox.localToScene(0, 0).getY();
            double max = fieldsBox.getHeight() - scroll.getViewportBounds().getHeight();
            if (max > 0) {
                scroll.setVvalue(Math.max(0, Math.min(1, (y - 40) / max)));
            }
        }
    }

    void show(ACHRecord record, String summary) {
        this.record = record;
        inputs.clear();
        controlsByStart.clear();
        fieldsBox.getChildren().clear();
        dirty.set(false);
        rawArea.setText(record == null || record.getRecord() == null ? "" : record.getRecord());
        if (record == null) {
            title.setText("Nothing selected");
            subtitle.setText("Select a record in the file to see and edit its fields here.");
            return;
        }
        title.setText(AchService.recordTypeLabel(record) + " · " + service.recordName(record)
                + (record.getLineNumber() > 0 ? "  (line " + record.getLineNumber() + ")" : ""));
        subtitle.setText(summary);

        fieldsBox.getChildren().add(rawPane);
        List<FieldView> fields = service.fields(record);
        for (int i = 0; i < fields.size(); i++) {
            FieldView f = fields.get(i);
            Label name = new Label(f.name());
            name.getStyleClass().add("field-name");
            HBox nameRow = new HBox(6, FieldColors.swatch(i), name);
            nameRow.setAlignment(Pos.CENTER_LEFT);
            Label pos = new Label("cols " + f.position() + " · " + f.length() + " char" + (f.length() == 1 ? "" : "s") + " · " + f.inclusion());
            pos.getStyleClass().add("field-pos");
            Control input = input(f);
            input.setMaxWidth(Double.MAX_VALUE);
            controlsByStart.put(f.start(), input);
            Label meaning = new Label(f.meaning());
            meaning.setWrapText(true);
            meaning.getStyleClass().add("field-meaning");
            if (f.meaning().contains("INVALID") || f.meaning().startsWith("Unknown") || f.meaning().startsWith("Not a")) {
                meaning.getStyleClass().add("field-bad");
            }
            VBox box = new VBox(2, nameRow, pos, input);
            if (!f.meaning().isBlank()) {
                box.getChildren().add(meaning);
            }
            box.getStyleClass().add("field-box");
            fieldsBox.getChildren().add(box);
        }
        Label hint = new Label("Amounts in dollars (1250.00). Dates as YYMMDD or YYYY-MM-DD. "
                + "Totals are recalculated automatically after edits to batches and entries.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        fieldsBox.getChildren().add(hint);
    }

    private Control input(FieldView f) {
        Map<String, String> codes = CODE_LISTS.get(f.name());
        if (f.editable() && codes != null) {
            ComboBox<String> combo = new ComboBox<>();
            codes.forEach((code, meaning) -> combo.getItems().add(code + " – " + meaning));
            String current = codes.containsKey(f.value()) ? f.value() + " – " + codes.get(f.value()) : f.value();
            if (!combo.getItems().contains(current)) {
                combo.getItems().add(0, current);
            }
            combo.setValue(current);
            Supplier<String> value = () -> {
                String v = combo.getValue() == null ? "" : combo.getValue();
                int dash = v.indexOf(" – ");
                return dash > 0 ? v.substring(0, dash) : v;
            };
            inputs.put(f, value);
            combo.valueProperty().addListener(o -> recomputeDirty());
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
            text.textProperty().addListener(o -> recomputeDirty());
            text.setOnAction(e -> apply());
        }
        return text;
    }

    private void recomputeDirty() {
        dirty.set(!edits().isEmpty());
    }
}
