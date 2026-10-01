package com.fx.ach;

import com.afrunt.jach.document.ACHBatch;
import com.afrunt.jach.document.ACHBatchDetail;
import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.ACHRecord;
import com.fx.ach.core.AchFormat;
import com.fx.ach.core.AchService;
import com.fx.ach.core.AchSummary;
import com.fx.ach.core.FieldView;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * "Not raw mode": the file laid out as nested collapsible forms (file → batches → entries →
 * addenda), each titled with a plain-English summary. Field grids are built lazily on expand.
 */
class FormView extends ScrollPane {

    private final AchService service;
    private final Consumer<ACHRecord> onSelect;
    private final Function<ACHRecord, ContextMenu> contextMenu;
    private final VBox content = new VBox(6);
    private final Map<ACHRecord, TitledPane> panes = new HashMap<>();
    private TitledPane highlighted;

    FormView(AchService service, Consumer<ACHRecord> onSelect, Function<ACHRecord, ContextMenu> contextMenu) {
        this.service = service;
        this.onSelect = onSelect;
        this.contextMenu = contextMenu;
        content.setPadding(new Insets(10));
        content.getStyleClass().add("form-view");
        setContent(content);
        setFitToWidth(true);
    }

    void show(ACHDocument doc) {
        panes.clear();
        highlighted = null;
        content.getChildren().clear();
        if (doc == null) {
            return;
        }
        AchSummary.FileInfo info = AchSummary.file(doc);
        content.getChildren().add(pane(doc.getFileHeader(),
                "File header · from " + info.originName() + " to " + info.destinationName() + " · created " + info.created(),
                "pane-file", false, List.of()));

        for (ACHBatch batch : doc.getBatches()) {
            AchSummary.BatchInfo b = AchSummary.batch(batch);
            VBox children = new VBox(4);
            children.getChildren().add(pane(batch.getBatchHeader(), "Batch header · " + b.secMeaning() + " · " + b.serviceClass()
                    + " · effective " + b.effectiveDate(), "pane-header", false, List.of()));
            for (ACHBatchDetail d : batch.getDetails()) {
                AchSummary.EntryInfo e = AchSummary.entry(service, d);
                List<Node> addenda = d.getAddendaRecords().stream()
                        .map(a -> (Node) pane(a, "Addenda " + a.getAddendaTypeCode() + " · " + AchSummary.addenda(a), "pane-addenda", false, List.of()))
                        .toList();
                String style = e.direction().toLowerCase().contains("debit") ? "pane-debit" : "pane-credit";
                children.getChildren().add(pane(d.getDetailRecord(), e.direction() + "  " + AchFormat.money(e.amount()) + "  ·  "
                        + e.name() + "  ·  " + e.accountType() + " " + e.account() + " @ " + e.routing()
                        + (e.note().isEmpty() ? "" : "  ·  " + e.note()), style, false, addenda));
            }
            if (batch.getBatchControl() != null) {
                children.getChildren().add(pane(batch.getBatchControl(), "Batch control · " + b.entries() + " entries · credits "
                        + AchFormat.money(b.credits()) + " · debits " + AchFormat.money(b.debits()), "pane-control", false, List.of()));
            }
            TitledPane batchPane = new TitledPane(b.title() + " · " + b.entries() + " entr" + (b.entries() == 1 ? "y" : "ies")
                    + " · " + AchFormat.money(b.credits().add(b.debits())), children);
            wire(batchPane, batch.getBatchHeader(), "pane-batch");
            batchPane.setExpanded(true);
            content.getChildren().add(batchPane);
        }

        content.getChildren().add(pane(doc.getFileControl(), "File control · " + info.batches() + " batch(es) · " + info.entries()
                + " entries · credits " + AchFormat.money(info.credits()) + " · debits " + AchFormat.money(info.debits()),
                "pane-file", false, List.of()));
    }

    /** Highlights the record's pane, expanding its ancestors and scrolling it into view. */
    void reveal(ACHRecord record) {
        if (highlighted != null) {
            highlighted.getStyleClass().remove("record-selected");
        }
        TitledPane p = panes.get(record);
        highlighted = p;
        if (p == null) {
            return;
        }
        p.getStyleClass().add("record-selected");
        for (Parent n = p.getParent(); n != null; n = n.getParent()) {
            if (n instanceof TitledPane t) {
                t.setExpanded(true);
            }
        }
        Platform.runLater(() -> {
            layout();
            double y = p.localToScene(0, 0).getY() - content.localToScene(0, 0).getY();
            double max = content.getHeight() - getViewportBounds().getHeight();
            double top = getVvalue() * max;
            double bottom = top + getViewportBounds().getHeight();
            if (max > 0 && (y < top || y + 30 > bottom)) {
                setVvalue(Math.max(0, Math.min(1, (y - 60) / max)));
            }
        });
    }

    private TitledPane pane(ACHRecord record, String title, String style, boolean expanded, List<Node> children) {
        VBox body = new VBox(6);
        TitledPane p = new TitledPane(title, body);
        p.setExpanded(expanded);
        p.setAnimated(false);
        Runnable fill = () -> {
            if (body.getChildren().isEmpty()) {
                body.getChildren().add(fieldGrid(record));
                body.getChildren().addAll(children);
            }
        };
        if (expanded) {
            fill.run();
        }
        p.expandedProperty().addListener((o, was, now) -> {
            if (now) {
                fill.run();
            }
        });
        wire(p, record, style);
        return p;
    }

    private void wire(TitledPane p, ACHRecord record, String style) {
        p.getStyleClass().add(style);
        panes.putIfAbsent(record, p);
        p.setOnMouseClicked(e -> {
            onSelect.accept(record);
            e.consume();
        });
        p.setOnContextMenuRequested(e -> {
            onSelect.accept(record);
            ContextMenu menu = contextMenu.apply(record);
            if (menu != null) {
                menu.show(p, e.getScreenX(), e.getScreenY());
            }
            e.consume();
        });
    }

    /** Read-only name / value / meaning grid; editing happens in the record panel. */
    private Node fieldGrid(ACHRecord record) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("mini-grid");
        grid.setHgap(10);
        grid.setVgap(3);
        ColumnConstraints c0 = new ColumnConstraints(210);
        ColumnConstraints c1 = new ColumnConstraints(90, 180, 260);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1, c2);
        List<FieldView> fields = service.fields(record);
        for (int i = 0; i < fields.size(); i++) {
            FieldView f = fields.get(i);
            if (f.inclusion().equals("Blank")) {
                continue;
            }
            Label name = new Label(f.name());
            HBox nameRow = new HBox(6, FieldColors.swatch(i), name);
            nameRow.setAlignment(Pos.CENTER_LEFT);
            Label value = new Label(f.value().isEmpty() ? "—" : f.value());
            value.getStyleClass().add("mono");
            Label meaning = new Label(f.meaning());
            meaning.getStyleClass().add("muted");
            meaning.setWrapText(true);
            if (f.meaning().contains("INVALID") || f.meaning().startsWith("Unknown")) {
                meaning.getStyleClass().add("field-bad");
            }
            int row = grid.getRowCount();
            grid.addRow(row, nameRow, value, meaning);
        }
        return grid;
    }
}
