package com.fx.ach;

import com.fx.ach.bai2.Bai2Account;
import com.fx.ach.bai2.Bai2Codes;
import com.fx.ach.bai2.Bai2Fields;
import com.fx.ach.bai2.Bai2File;
import com.fx.ach.bai2.Bai2Format;
import com.fx.ach.bai2.Bai2Group;
import com.fx.ach.bai2.Bai2Issue;
import com.fx.ach.bai2.Bai2Line;
import com.fx.ach.bai2.Bai2Record;
import com.fx.ach.bai2.Bai2Report;
import com.fx.ach.bai2.Bai2Summary;
import com.fx.ach.bai2.Bai2Transaction;
import com.fx.ach.bai2.Bai2Validator;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A read-only tab for one BAI2 file: summary cards on top, the raw lines (each field shaded) or,
 * in Present mode, a File → Group → Account → Transaction table, and the selected record's fields
 * explained on the right.
 */
class Bai2View extends BorderPane {

    /** One row of the Present-mode table. */
    private record Row(String item, String code, String description, String amount, String funds,
                       String bankRef, String customerRef, String text, Bai2Record record, String style) {
    }

    final Tab tab = new Tab();
    private final Bai2File file;
    private final Path path;
    private final List<Bai2Issue> issues;
    private final ListView<Bai2Line> rawList = new ListView<>();
    private final TreeTableView<Row> tree = new TreeTableView<>();
    private final Map<Bai2Line, Integer> lineIndex = new HashMap<>();
    private final Map<Bai2Record, TreeItem<Row>> treeItems = new IdentityHashMap<>();
    private final VBox detail = new VBox(10);
    private boolean syncing;
    private Bai2Record selected;

    Bai2View(Bai2File file, Path path) {
        this.file = file;
        this.path = path;
        this.issues = Bai2Validator.validate(file);
        getStyleClass().add("bai2-view");

        setTop(summary());

        rawList.getStyleClass().add("raw-list");
        rawList.getItems().setAll(file.lines());
        for (int i = 0; i < file.lines().size(); i++) {
            lineIndex.put(file.lines().get(i), i);
        }
        rawList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        rawList.setCellFactory(l -> new RawCell());
        rawList.getSelectionModel().selectedItemProperty().addListener((o, was, line) -> {
            if (!syncing && line != null) {
                select(file.recordOf(line));
            }
        });

        buildTree();
        tree.getSelectionModel().selectedItemProperty().addListener((o, was, item) -> {
            if (!syncing && item != null && item.getValue().record() != null) {
                select(item.getValue().record());
            }
        });
        tree.setVisible(false);

        detail.setPadding(new Insets(10, 12, 16, 12));
        ScrollPane detailScroll = new ScrollPane(detail);
        detailScroll.setFitToWidth(true);
        detailScroll.getStyleClass().add("record-panel");
        detailScroll.setMinWidth(320);

        SplitPane split = new SplitPane(new StackPane(rawList, tree), detailScroll);
        split.setDividerPositions(0.64);
        SplitPane.setResizableWithParent(detailScroll, false);
        setCenter(split);

        tab.setContent(this);
        tab.setUserData(this);
        tab.setText(title());
        tab.setTooltip(new Tooltip((path == null ? "Pasted text" : path.toString()) + " · BAI2, read-only"));
        select(file.records().isEmpty() ? null : file.records().get(0));
    }

    Bai2File file() {
        return file;
    }

    Path path() {
        return path;
    }

    List<Bai2Issue> issues() {
        return issues;
    }

    String title() {
        return path == null ? "Pasted BAI2" : path.getFileName().toString();
    }

    String report() {
        return Bai2Report.html(file, title());
    }

    void setPresent(boolean present) {
        tree.setVisible(present);
        rawList.setVisible(!present);
        if (selected != null) {
            select(selected);
        }
    }

    // ---- summary -----------------------------------------------------------------------------

    private Region summary() {
        List<Bai2Account> accounts = file.accounts();
        int transactions = accounts.stream().mapToInt(a -> a.transactions().size()).sum();
        FlowPane cards = new FlowPane(10, 10,
                card("FROM", file.sender(), null),
                card("TO", file.receiver(), null),
                card("CREATED", Bai2Format.readableDate(file.creationDate())
                        + (file.creationTime().isEmpty() ? "" : " " + Bai2Format.readableTime(file.creationTime())), null),
                card("GROUPS / ACCOUNTS", file.groups().size() + " / " + accounts.size(), null),
                card("TRANSACTIONS", String.valueOf(transactions), null),
                card("CREDITS", Bai2Report.totals(accounts, true), "credit"),
                card("DEBITS", Bai2Report.totals(accounts, false), "debit"));
        cards.setPadding(new Insets(10, 12, 10, 12));
        return cards;
    }

    private static VBox card(String label, String value, String style) {
        Label l = new Label(label);
        l.getStyleClass().add("card-label");
        Label v = new Label(value.isEmpty() ? "—" : value);
        v.getStyleClass().addAll("card-value", "bai2-card-value");
        if (style != null) {
            v.getStyleClass().add(style);
        }
        VBox box = new VBox(2, l, v);
        box.getStyleClass().add("card");
        return box;
    }

    // ---- raw view ----------------------------------------------------------------------------

    /** One physical line: its number, then each field shaded in its own colour with a tooltip. */
    private final class RawCell extends ListCell<Bai2Line> {
        @Override
        protected void updateItem(Bai2Line line, boolean empty) {
            super.updateItem(line, empty);
            setText(null);
            if (empty || line == null) {
                setGraphic(null);
                return;
            }
            Bai2Record r = file.recordOf(line);
            Label number = new Label(String.format("%4d  ", line.number()));
            number.getStyleClass().add("line-number");
            HBox box = new HBox(number);
            box.setAlignment(Pos.CENTER_LEFT);
            List<Bai2Fields.Field> fields = Bai2Fields.fields(file, r);
            List<Bai2Fields.Segment> segs = Bai2Fields.segments(file, r).get(r.lines().indexOf(line));
            for (Bai2Fields.Segment s : segs) {
                Label seg = new Label(s.text());
                seg.getStyleClass().add("seg");
                seg.setMinWidth(Region.USE_PREF_SIZE);
                if (s.field() < 0) {
                    seg.getStyleClass().addAll("bai2-code", "bai2-code-" + line.code());
                } else {
                    seg.setStyle("-fx-background-color: " + FieldColors.color(s.field()) + ";");
                }
                Bai2Fields.Field f = s.field() >= 0 && s.field() < fields.size() ? fields.get(s.field()) : null;
                String tip = s.name() + (f == null ? "" : "\nValue: " + (f.value().isEmpty() ? "(blank)" : "\"" + f.value() + "\"")
                        + (f.meaning().isEmpty() ? "" : "\nMeaning: " + f.meaning()));
                Tooltip t = new Tooltip(tip);
                t.setShowDelay(Duration.millis(150));
                t.setShowDuration(Duration.seconds(30));
                Tooltip.install(seg, t);
                box.getChildren().add(seg);
            }
            setGraphic(box);
        }
    }

    // ---- present mode ------------------------------------------------------------------------

    private void buildTree() {
        tree.getStyleClass().add("bai2-tree");
        tree.getColumns().add(column("Item", Row::item, 210));
        tree.getColumns().add(column("Code", Row::code, 55));
        tree.getColumns().add(column("Description", Row::description, 260));
        TreeTableColumn<Row, String> amount = column("Amount", Row::amount, 120);
        amount.setStyle("-fx-alignment: CENTER-RIGHT;");
        tree.getColumns().add(amount);
        tree.getColumns().add(column("Availability", Row::funds, 150));
        tree.getColumns().add(column("Bank ref", Row::bankRef, 110));
        tree.getColumns().add(column("Customer ref", Row::customerRef, 110));
        tree.getColumns().add(column("Text", Row::text, 280));
        tree.setRowFactory(t -> new TreeTableRow<>() {
            @Override
            protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().removeAll("row-file", "row-group", "row-account", "row-credit", "row-debit", "row-trailer");
                if (!empty && row != null && row.style() != null) {
                    getStyleClass().add(row.style());
                }
            }
        });

        Bai2Record h = file.header();
        TreeItem<Row> root = item(new Row("File " + file.fileId(), "01", h == null ? "File header missing" : Bai2Fields.describe(file, h),
                "", "", "", "", "", h, "row-file"));
        int gn = 0;
        for (Bai2Group g : file.groups()) {
            TreeItem<Row> group = item(new Row("Group " + (++gn), "02", g.header() == null ? "Group header missing" : Bai2Fields.describe(file, g.header()),
                    "", "", "", "", "", g.header(), "row-group"));
            for (Bai2Account a : g.accounts()) {
                String cur = a.currency();
                TreeItem<Row> account = item(new Row("Account " + a.accountNumber(), cur,
                        a.transactions().size() + " transaction(s) · credits " + Bai2Format.money(a.credits(), cur)
                                + " · debits " + Bai2Format.money(a.debits(), cur), "", "", "", "", "", a.header(), "row-account"));
                for (Bai2Summary s : a.summaries()) {
                    account.getChildren().add(new TreeItem<>(new Row(Bai2Codes.kind(s.typeCode()) == Bai2Codes.Kind.BALANCE ? "Balance" : "Summary",
                            s.typeCode(), Bai2Codes.typeName(s.typeCode()), Bai2Format.money(s.amountRaw(), cur),
                            s.funds().type().isEmpty() ? "" : s.funds().describe(cur), "", "",
                            s.itemCount().isEmpty() ? "" : s.itemCount() + " item(s)", a.header(), null)));
                }
                for (Bai2Transaction tx : a.transactions()) {
                    String style = tx.kind() == Bai2Codes.Kind.CREDIT ? "row-credit" : tx.kind() == Bai2Codes.Kind.DEBIT ? "row-debit" : null;
                    account.getChildren().add(item(new Row(tx.kind().label, tx.typeCode(), Bai2Codes.typeName(tx.typeCode()),
                            Bai2Format.money(tx.amountRaw(), cur), tx.funds().describe(cur), tx.bankReference(), tx.customerReference(),
                            tx.text(), tx.record(), style)));
                }
                trailer(account, "Account trailer", a.trailer());
                account.setExpanded(true);
                group.getChildren().add(account);
            }
            trailer(group, "Group trailer", g.trailer());
            group.setExpanded(true);
            root.getChildren().add(group);
        }
        trailer(root, "File trailer", file.trailer());
        root.setExpanded(true);
        tree.setRoot(root);
    }

    private void trailer(TreeItem<Row> parent, String name, Bai2Record r) {
        if (r != null) {
            parent.getChildren().add(item(new Row(name, r.code(), Bai2Fields.describe(file, r), "", "", "", "", "", r, "row-trailer")));
        }
    }

    private TreeItem<Row> item(Row row) {
        TreeItem<Row> item = new TreeItem<>(row);
        if (row.record() != null) {
            treeItems.putIfAbsent(row.record(), item);
        }
        return item;
    }

    private static TreeTableColumn<Row, String> column(String title, Function<Row, String> value, double width) {
        TreeTableColumn<Row, String> c = new TreeTableColumn<>(title);
        c.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getValue() == null ? "" : value.apply(p.getValue().getValue())));
        c.setPrefWidth(width);
        return c;
    }

    // ---- selection and detail ------------------------------------------------------------------

    /** Selects a record in whichever view is showing and explains it on the right. */
    void select(Bai2Record r) {
        selected = r;
        showDetail(r);
        if (r == null) {
            return;
        }
        syncing = true;
        try {
            rawList.getSelectionModel().clearSelection();
            int first = -1;
            for (Bai2Line l : r.lines()) {
                Integer i = lineIndex.get(l);
                if (i != null) {
                    rawList.getSelectionModel().select(i);
                    first = first < 0 ? i : first;
                }
            }
            if (first >= 0 && rawList.isVisible()) {
                rawList.scrollTo(Math.max(0, first - 3));
            }
            TreeItem<Row> item = treeItems.get(r);
            TreeItem<Row> current = tree.getSelectionModel().getSelectedItem();
            if (current != null && current.getValue().record() == r) {
                item = current; // e.g. a balance row, which shares its account's 03 record
            }
            if (item != null) {
                tree.getSelectionModel().select(item);
                int row = tree.getRow(item);
                if (row >= 0 && tree.isVisible()) {
                    tree.scrollTo(Math.max(0, row - 3));
                }
            } else {
                tree.getSelectionModel().clearSelection();
            }
        } finally {
            syncing = false;
        }
    }

    private void showDetail(Bai2Record r) {
        detail.getChildren().clear();
        Label caption = new Label("RECORD · READ-ONLY");
        caption.getStyleClass().add("pane-title-inline");
        Label title = new Label(r == null ? "Nothing selected" : Bai2Fields.recordTypeLabel(r) + "  (line " + r.lineNumber() + ")");
        title.getStyleClass().add("h2");
        title.setWrapText(true);
        Label subtitle = new Label(r == null ? "Select a line to see its fields explained here." : Bai2Fields.describe(file, r));
        subtitle.getStyleClass().add("muted");
        subtitle.setWrapText(true);
        detail.getChildren().addAll(caption, title, subtitle);
        if (r == null) {
            return;
        }
        TextArea raw = new TextArea(r.raw());
        raw.setEditable(false);
        raw.getStyleClass().add("mono");
        raw.setPrefRowCount(Math.min(4, r.lines().size()));
        raw.setWrapText(true);
        detail.getChildren().add(raw);

        Label code = new Label("Record code " + r.code() + " · " + Bai2Fields.recordTypeLabel(r)
                + (r.lines().size() > 1 ? " · " + (r.lines().size() - 1) + " continuation line(s)" : ""));
        code.getStyleClass().add("field-pos");
        detail.getChildren().add(code);

        List<Bai2Fields.Field> fields = Bai2Fields.fields(file, r);
        for (int i = 0; i < fields.size(); i++) {
            Bai2Fields.Field f = fields.get(i);
            Label name = new Label(f.name());
            name.getStyleClass().add("field-name");
            HBox nameRow = new HBox(6, FieldColors.swatch(i), name);
            nameRow.setAlignment(Pos.CENTER_LEFT);
            Region value;
            if (f.name().equals("Text")) {
                TextArea area = new TextArea(f.value());
                area.setWrapText(true);
                area.setPrefRowCount(2);
                area.setEditable(false);
                value = area;
            } else {
                TextField field = new TextField(f.value());
                field.setEditable(false);
                value = field;
            }
            value.getStyleClass().addAll("mono", "read-only");
            value.setMaxWidth(Double.MAX_VALUE);
            VBox box = new VBox(2, nameRow, value);
            if (!f.meaning().isBlank()) {
                Label meaning = new Label(f.meaning());
                meaning.setWrapText(true);
                meaning.getStyleClass().add("field-meaning");
                if (f.meaning().contains("not a valid") || f.meaning().startsWith("Unknown") || f.meaning().startsWith("Expected")) {
                    meaning.getStyleClass().add("field-bad");
                }
                box.getChildren().add(meaning);
            }
            box.getStyleClass().add("field-box");
            VBox.setVgrow(box, Priority.NEVER);
            detail.getChildren().add(box);
        }
        Label hint = new Label("BAI2 files are bank balance and transaction reports, shown read-only. "
                + "Amounts are in the account currency's minor units (cents for USD).");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        detail.getChildren().add(hint);
    }
}
