package com.fx.ach;

import com.afrunt.jach.document.ACHBatch;
import com.afrunt.jach.document.ACHBatchDetail;
import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.ACHRecord;
import com.fx.ach.core.AchControls;
import com.fx.ach.core.AchFormat;
import com.fx.ach.core.AchService;
import com.fx.ach.core.AchSummary;
import com.fx.ach.core.AchValidator;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The "what is in this file" page: who it is from and to, totals, and every payment per batch.
 */
class OverviewView {

    /** Entry summary plus the record it came from, so a row can be opened. */
    record Row(int number, AchSummary.EntryInfo info, ACHRecord record) {
    }

    private final AchService service;
    private final ACHDocument doc;
    private final List<String> notes;
    private final List<AchValidator.Issue> issues;
    private final Consumer<ACHRecord> open;

    OverviewView(AchService service, ACHDocument doc, List<String> notes, List<AchValidator.Issue> issues, Consumer<ACHRecord> open) {
        this.service = service;
        this.doc = doc;
        this.notes = notes;
        this.issues = issues;
        this.open = open;
    }

    static Node empty() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(40));
        Label title = new Label("No file open");
        title.getStyleClass().add("h1");
        Label hint = new Label("• File ▸ Open… (or drag an ACH file onto this window) to read a file.\n"
                + "• File ▸ New from template to build a payroll, vendor-payment or collections file.\n"
                + "• Every record is shown as a form with plain-English meanings; nothing is hidden in 94-character lines.");
        hint.getStyleClass().add("muted");
        box.getChildren().addAll(title, hint);
        return box;
    }

    Node build() {
        AchSummary.FileInfo f = AchSummary.file(doc);
        VBox box = new VBox(14);
        box.setPadding(new Insets(16));

        Label title = new Label("From " + blank(f.originName()) + " to " + blank(f.destinationName()));
        title.getStyleClass().add("h1");
        Hyperlink created = new Hyperlink("Created " + f.created() + " · File ID modifier " + f.fileId() + " · view file header");
        created.setOnAction(e -> open.accept(doc.getFileHeader()));

        FlowPane cards = new FlowPane(10, 10);
        cards.getChildren().addAll(
                card("Sender (origin)", blank(f.originName()), f.origin()),
                card("Receiver (destination)", blank(f.destinationName()), AchFormat.describeRouting(f.destination())),
                card("Total credits", AchFormat.money(f.credits()), "money paid out to receivers", "credit"),
                card("Total debits", AchFormat.money(f.debits()), "money collected from receivers", "debit"),
                card("Batches · entries", f.batches() + " · " + f.entries(), recordCountText()),
                card("Validation", issues.isEmpty() ? "No problems" : issues.size() + " issue(s)",
                        issues.isEmpty() ? "totals, hashes and check digits agree" : "see the Validation panel below",
                        issues.isEmpty() ? "credit" : "debit"));
        box.getChildren().addAll(title, created, cards);

        if (!notes.isEmpty()) {
            Label n = new Label("Read notes: " + String.join(" ", notes));
            n.getStyleClass().add("note");
            n.setWrapText(true);
            box.getChildren().add(n);
        }

        for (ACHBatch batch : doc.getBatches()) {
            box.getChildren().add(batchSection(batch));
        }
        return box;
    }

    private String recordCountText() {
        return AchControls.recordCount(doc) + " records in "
                + AchControls.blockCount(doc) + " block(s)";
    }

    private Node batchSection(ACHBatch batch) {
        AchSummary.BatchInfo b = AchSummary.batch(batch);
        Hyperlink title = new Hyperlink(b.title());
        title.getStyleClass().add("h2");
        title.setOnAction(e -> open.accept(batch.getBatchHeader()));
        Label sub = new Label(b.secMeaning() + "  ·  " + b.serviceClass() + "  ·  Effective " + b.effectiveDate()
                + "  ·  Company ID " + b.companyId() + "  ·  Originating bank " + b.odfi());
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);

        TableView<Row> table = new TableView<>();
        table.getStyleClass().add("entries");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(col("#", 34, r -> String.valueOf(r.number())));
        table.getColumns().add(col("Receiver", 160, r -> r.info().name()));
        table.getColumns().add(col("ID", 90, r -> r.info().idNumber()));
        table.getColumns().add(col("Routing", 86, r -> r.info().routing()));
        table.getColumns().add(col("Account", 120, r -> r.info().account()));
        table.getColumns().add(col("Account type", 100, r -> r.info().accountType()));
        TableColumn<Row, String> direction = col("Direction", 80, r -> r.info().direction());
        direction.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                getStyleClass().removeAll("credit", "debit");
                setText(empty ? null : v);
                if (!empty && v != null) {
                    getStyleClass().add(v.startsWith("Debit") ? "debit" : v.startsWith("Credit") ? "credit" : "muted");
                }
            }
        });
        table.getColumns().add(direction);
        TableColumn<Row, BigDecimal> amount = new TableColumn<>("Amount");
        amount.setPrefWidth(100);
        amount.setMinWidth(100);
        amount.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().info().amount()));
        amount.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : AchFormat.money(v));
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        table.getColumns().add(amount);
        table.getColumns().add(col("Trace number", 132, r -> r.info().trace()));
        table.getColumns().add(col("Notes / addenda", 220, r -> r.info().note()));

        int i = 0;
        for (ACHBatchDetail d : batch.getDetails()) {
            table.getItems().add(new Row(++i, AchSummary.entry(service, d), d.getDetailRecord()));
        }
        table.setRowFactory(t -> {
            TableRow<Row> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    open.accept(row.getItem().record());
                }
            });
            return row;
        });
        table.setPlaceholder(new Label("No entries in this batch yet – use Edit ▸ Add Entry."));
        table.setFixedCellSize(26);
        table.setPrefHeight(Math.min(30 + 26 * Math.max(table.getItems().size(), 2) + 4, 460));

        Label totals = new Label(b.entries() + " entries  ·  credits " + AchFormat.money(b.credits())
                + "  ·  debits " + AchFormat.money(b.debits()) + "      (double-click a row to open it as a form)");
        totals.getStyleClass().add("muted");

        VBox section = new VBox(4, title, sub, table, new HBox(totals));
        section.getStyleClass().add("batch-section");
        return section;
    }

    private static TableColumn<Row, String> col(String title, double width, Function<Row, String> value) {
        TableColumn<Row, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setMinWidth(width);
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return c;
    }

    private static Node card(String label, String value, String detail, String... extraStyle) {
        Label l = new Label(label.toUpperCase());
        l.getStyleClass().add("card-label");
        Label v = new Label(value);
        v.getStyleClass().add("card-value");
        v.getStyleClass().addAll(extraStyle);
        Label d = new Label(detail);
        d.getStyleClass().add("muted");
        VBox card = new VBox(2, l, v, d);
        card.getStyleClass().add("card");
        card.setMinWidth(200);
        return card;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? "(unnamed)" : s;
    }
}
