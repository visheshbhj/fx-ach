package com.fx.ach;

import com.afrunt.jach.document.ACHBatch;
import com.afrunt.jach.document.ACHBatchDetail;
import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.ACHRecord;
import com.afrunt.jach.domain.AddendaRecord;
import com.afrunt.jach.domain.BatchControl;
import com.afrunt.jach.domain.FileControl;
import com.fx.ach.core.AchBuilder;
import com.fx.ach.core.AchCodes;
import com.fx.ach.core.AchControls;
import com.fx.ach.core.AchException;
import com.fx.ach.core.AchFormat;
import com.fx.ach.core.AchReport;
import com.fx.ach.core.AchService;
import com.fx.ach.core.AchSummary;
import com.fx.ach.core.AchTemplates;
import com.fx.ach.core.AchValidator;
import com.fx.ach.core.FieldView;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MainController {

    /** A row in the structure tree. */
    record Node(String label, ACHRecord record, String styleClass) {
    }

    @FXML private BorderPane root;
    @FXML private Menu newMenu;
    @FXML private MenuButton newButton;
    @FXML private Label statusBadge;
    @FXML private TreeView<Node> tree;
    @FXML private TabPane tabs;
    @FXML private Tab overviewTab;
    @FXML private Tab recordTab;
    @FXML private Tab rawTab;
    @FXML private ScrollPane overviewScroll;
    @FXML private ScrollPane recordScroll;
    @FXML private ListView<ACHRecord> rawList;
    @FXML private Label ruler;
    @FXML private ListView<AchValidator.Issue> issuesList;
    @FXML private Label issuesTitle;
    @FXML private Label statusLabel;

    private final AchService service = new AchService();
    private final Deque<String> undo = new ArrayDeque<>();
    private Stage stage;
    private ACHDocument doc;
    private Path file;
    private boolean dirty;
    private List<String> readNotes = List.of();
    private ACHRecord selected;
    private boolean syncing;

    void init(Stage stage) {
        this.stage = stage;
        rebuildNewMenus();
        tree.setCellFactory(t -> new TreeCell<>() {
            @Override
            protected void updateItem(Node item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeIf(s -> s.startsWith("node-"));
                setText(empty || item == null ? null : item.label());
                if (!empty && item != null) {
                    getStyleClass().add(item.styleClass());
                }
            }
        });
        tree.getSelectionModel().selectedItemProperty().addListener((o, a, item) -> {
            if (!syncing && item != null && item.getValue().record() != null) {
                select(item.getValue().record(), false);
            }
        });

        rawList.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(ACHRecord r, boolean empty) {
                super.updateItem(r, empty);
                getStyleClass().removeIf(s -> s.startsWith("rt-"));
                if (empty || r == null) {
                    setText(null);
                } else {
                    setText(String.format("%4d  %s", r.getLineNumber(), r.getRecord()));
                    getStyleClass().add("rt-" + r.getRecordTypeCode());
                }
            }
        });
        rawList.getSelectionModel().selectedItemProperty().addListener((o, a, r) -> {
            if (!syncing && r != null) {
                select(r, false);
            }
        });
        rawList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && selected != null) {
                tabs.getSelectionModel().select(recordTab);
            }
        });
        ruler.setText(rulerText());

        issuesList.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(AchValidator.Issue issue, boolean empty) {
                super.updateItem(issue, empty);
                getStyleClass().removeAll("issue-error", "issue-warning");
                if (empty || issue == null) {
                    setText(null);
                } else {
                    setText((issue.severity() == AchValidator.Severity.ERROR ? "✖  " : "⚠  ") + issue);
                    getStyleClass().add(issue.severity() == AchValidator.Severity.ERROR ? "issue-error" : "issue-warning");
                }
            }
        });
        issuesList.getSelectionModel().selectedItemProperty().addListener((o, a, issue) -> {
            if (!syncing && issue != null && issue.record() != null) {
                select(issue.record(), true);
            }
        });

        root.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        root.setOnDragDropped(e -> {
            List<File> files = e.getDragboard().getFiles();
            if (!files.isEmpty() && confirmDiscard()) {
                open(files.get(0).toPath());
            }
            e.setDropCompleted(true);
            e.consume();
        });
        stage.setOnCloseRequest(e -> {
            if (!confirmDiscard()) {
                e.consume();
            }
        });
        refresh();
    }

    // ---- file actions ----------------------------------------------------------------------

    private void rebuildNewMenus() {
        newMenu.getItems().setAll(templateItems());
        newButton.getItems().setAll(templateItems());
    }

    private List<MenuItem> templateItems() {
        List<MenuItem> items = new ArrayList<>();
        for (AchTemplates.BuiltIn t : AchTemplates.BuiltIn.values()) {
            MenuItem item = new MenuItem(t.title);
            item.setOnAction(e -> newFromTemplate(t));
            items.add(item);
        }
        List<Path> user = AchTemplates.userTemplates();
        if (!user.isEmpty()) {
            items.add(new SeparatorMenuItem());
            for (Path p : user) {
                MenuItem item = new MenuItem("My template: " + p.getFileName());
                item.setOnAction(e -> newFromUserTemplate(p));
                items.add(item);
            }
        }
        return items;
    }

    private void newFromTemplate(AchTemplates.BuiltIn template) {
        if (!confirmDiscard()) {
            return;
        }
        Dialogs.newFile(stage, template).ifPresent(created -> load(service.normalise(created), null, List.of(),
                "New file from template \"" + template.title + "\". Edit entries, then Save."));
    }

    private void newFromUserTemplate(Path template) {
        if (!confirmDiscard()) {
            return;
        }
        Dialogs.userTemplate(stage, template).ifPresent(opts -> {
            try {
                ACHDocument created = AchTemplates.instantiate(service, template, opts.effectiveDate(), opts.clearAmounts());
                load(service.normalise(created), null, List.of(), "New file from template " + template.getFileName() + ".");
            } catch (IOException | AchException e) {
                error("Could not use template", e);
            }
        });
    }

    @FXML
    private void onOpen() {
        if (!confirmDiscard()) {
            return;
        }
        FileChooser chooser = chooser("Open ACH file");
        File f = chooser.showOpenDialog(stage);
        if (f != null) {
            open(f.toPath());
        }
    }

    void open(Path path) {
        try {
            AchService.ReadResult result = service.read(path);
            load(result.document(), path, result.notes(), "Opened " + path.getFileName() + ".");
            Prefs.setLastDir(path.getParent());
        } catch (IOException | AchException e) {
            error("Could not read " + path.getFileName(), e);
        }
    }

    private void load(ACHDocument document, Path path, List<String> notes, String status) {
        doc = document;
        file = path;
        readNotes = notes;
        dirty = path == null;
        undo.clear();
        selected = doc.getFileHeader();
        refresh();
        tabs.getSelectionModel().select(overviewTab);
        status(status);
    }

    @FXML
    private void onSave() {
        if (doc == null) {
            return;
        }
        if (file == null) {
            onSaveAs();
        } else {
            save(file);
        }
    }

    @FXML
    private void onSaveAs() {
        if (doc == null) {
            return;
        }
        FileChooser chooser = chooser("Save ACH file");
        chooser.setInitialFileName(file == null ? "payments.ach" : file.getFileName().toString());
        File f = chooser.showSaveDialog(stage);
        if (f != null) {
            save(f.toPath());
        }
    }

    private void save(Path target) {
        long errors = AchValidator.validate(doc).stream().filter(i -> i.severity() == AchValidator.Severity.ERROR).count();
        if (errors > 0) {
            ButtonType fix = new ButtonType("Fix totals, then save", ButtonBar.ButtonData.YES);
            ButtonType anyway = new ButtonType("Save as is", ButtonBar.ButtonData.NO);
            Alert alert = new Alert(Alert.AlertType.WARNING, errors + " validation error(s) were found. Banks will usually "
                    + "reject a file whose control totals do not match its entries.", fix, anyway, ButtonType.CANCEL);
            alert.initOwner(stage);
            alert.setHeaderText("This file has problems");
            Optional<ButtonType> choice = alert.showAndWait();
            if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) {
                return;
            }
            if (choice.get() == fix) {
                mutate("Recalculated controls", () -> AchControls.recalculate(doc));
            }
        }
        try {
            Files.writeString(target, service.write(doc, true), StandardCharsets.ISO_8859_1);
            file = target;
            dirty = false;
            Prefs.setLastDir(target.getParent());
            updateTitle();
            status("Saved " + target + ".");
        } catch (IOException | AchException e) {
            error("Could not save", e);
        }
    }

    @FXML
    private void onSaveTemplate() {
        if (doc == null) {
            return;
        }
        TextInputDialog input = new TextInputDialog(file == null ? "My template" : file.getFileName().toString().replaceFirst("\\.[^.]*$", ""));
        input.initOwner(stage);
        input.setHeaderText("Save this file as a reusable template.\nNew files made from it get today's date and a new effective date.");
        input.setContentText("Template name:");
        input.showAndWait().ifPresent(name -> {
            try {
                Path saved = AchTemplates.saveUserTemplate(service, doc, name);
                rebuildNewMenus();
                status("Template saved to " + saved + ". It now appears under File ▸ New from template.");
            } catch (IOException | AchException e) {
                error("Could not save template", e);
            }
        });
    }

    @FXML
    private void onExportReport() {
        if (doc == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export readable report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML page", "*.html"));
        chooser.setInitialFileName((file == null ? "ach-report" : file.getFileName().toString().replaceFirst("\\.[^.]*$", "")) + ".html");
        Prefs.lastDir().ifPresent(d -> chooser.setInitialDirectory(d.toFile()));
        File f = chooser.showSaveDialog(stage);
        if (f == null) {
            return;
        }
        try {
            Files.writeString(f.toPath(), AchReport.html(service, doc, file == null ? "(unsaved)" : file.getFileName().toString()));
            status("Report written to " + f + ". Open it in a browser to read or print.");
        } catch (IOException e) {
            error("Could not write report", e);
        }
    }

    @FXML
    private void onExit() {
        if (confirmDiscard()) {
            Platform.exit();
        }
    }

    // ---- edit actions ----------------------------------------------------------------------

    @FXML
    private void onUndo() {
        if (undo.isEmpty()) {
            status("Nothing to undo.");
            return;
        }
        int index = selectedIndex();
        doc = service.read(undo.pop()).document();
        dirty = true;
        selected = recordAt(index);
        refresh();
        status("Undone.");
    }

    @FXML
    private void onAddBatch() {
        if (doc == null) {
            return;
        }
        ACHBatch current = selectedBatch();
        ACHBatch template = current != null ? current : doc.getBatches().isEmpty() ? null : doc.getBatches().get(doc.getBatches().size() - 1);
        Dialogs.batch(stage, template == null ? null : template.getBatchHeader()).ifPresent(batch -> {
            mutate("Added batch", () -> {
                batch.setBatchControl(new BatchControl());
                doc.addBatch(batch);
                AchControls.recalculate(doc);
            });
            selected = doc.getBatches().get(doc.getBatches().size() - 1).getBatchHeader();
            refresh();
            tabs.getSelectionModel().select(recordTab);
        });
    }

    @FXML
    private void onAddEntry() {
        if (doc == null) {
            return;
        }
        ACHBatch batch = selectedBatch();
        if (batch == null) {
            if (doc.getBatches().size() == 1) {
                batch = doc.getBatches().get(0);
            } else {
                info("Select a batch (or an entry inside it) first, then add the entry.");
                return;
            }
        }
        String sec = AchFormat.trim(batch.getBatchHeader().getStandardEntryClassCode());
        if (!AchBuilder.BUILDABLE_SEC.contains(sec)) {
            info("Entries can be added from a form for PPD, CCD, WEB and TEL batches. This batch is " + sec
                    + "; edit an existing entry's fields instead.");
            return;
        }
        ACHBatch target = batch;
        int batchIndex = doc.getBatches().indexOf(batch);
        Dialogs.entry(stage, sec).ifPresent(detail -> {
            mutate("Added entry", () -> {
                target.addDetail(detail);
                AchControls.renumberTraces(doc);
                AchControls.recalculate(doc);
            });
            List<ACHBatchDetail> details = doc.getBatches().get(batchIndex).getDetails();
            if (!details.isEmpty()) {
                selected = details.get(details.size() - 1).getDetailRecord();
                refresh();
            }
        });
    }

    @FXML
    private void onAddAddenda() {
        ACHBatchDetail detail = selectedDetail();
        if (detail == null) {
            info("Select an entry first.");
            return;
        }
        TextInputDialog input = new TextInputDialog();
        input.initOwner(stage);
        input.setHeaderText("Payment-related information (addenda type 05), up to 80 characters.\nExample: INV 10045 PO 2231");
        input.setContentText("Text:");
        input.showAndWait().filter(s -> !s.isBlank()).ifPresent(text -> mutate("Added addenda", () -> {
            detail.addAddendaRecord(AchBuilder.newAddenda(text.trim()));
            AchControls.renumberTraces(doc);
            AchControls.recalculate(doc);
        }));
    }

    @FXML
    private void onDelete() {
        if (doc == null || selected == null) {
            return;
        }
        String what = describe(selected);
        if (selected == doc.getFileHeader() || selected == doc.getFileControl()) {
            info("The file header and file control can't be deleted.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete " + what + "?", ButtonType.OK, ButtonType.CANCEL);
        confirm.initOwner(stage);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        ACHRecord target = selected;
        int index = selectedIndex();
        mutate("Deleted " + what, () -> {
            for (ACHBatch batch : new ArrayList<>(doc.getBatches())) {
                if (batch.getBatchHeader() == target || batch.getBatchControl() == target) {
                    doc.getBatches().remove(batch);
                    break;
                }
                for (ACHBatchDetail d : new ArrayList<>(batch.getDetails())) {
                    if (d.getDetailRecord() == target) {
                        batch.getDetails().remove(d);
                    } else if (d.getAddendaRecords().contains(target)) {
                        d.getAddendaRecords().remove(target);
                    }
                }
            }
            AchControls.renumberTraces(doc);
            AchControls.recalculate(doc);
        });
        selected = recordAt(Math.max(0, index - 1));
        refresh();
    }

    @FXML
    private void onRecalculate() {
        if (doc != null) {
            mutate("Recalculated batch and file control totals", () -> AchControls.recalculate(doc));
        }
    }

    @FXML
    private void onRenumber() {
        if (doc != null) {
            mutate("Renumbered trace numbers", () -> {
                AchControls.renumberTraces(doc);
                AchControls.recalculate(doc);
            });
        }
    }

    /** Applies field edits made in the record form. */
    void applyEdits(ACHRecord record, Map<FieldView, String> edits) {
        if (edits.isEmpty()) {
            select(record, false);
            status("No changes – form reset to the record's current values.");
            return;
        }
        int index = selectedIndex();
        boolean isControl = record instanceof BatchControl || record instanceof FileControl;
        mutate("Updated " + edits.size() + " field(s)", () -> {
            ACHRecord updated = record;
            for (Map.Entry<FieldView, String> edit : edits.entrySet()) {
                FieldView current = service.fields(updated).stream()
                        .filter(f -> f.start() == edit.getKey().start()).findFirst().orElseThrow();
                updated = service.withField(updated, current, edit.getValue());
            }
            AchService.replace(doc, record, updated);
            if (!isControl) {
                AchControls.recalculate(doc);
            }
        });
        selected = recordAt(index);
        refresh();
    }

    /**
     * Runs a change against the document. The document is round-tripped through text so every
     * record stays consistent; on failure the previous state is restored.
     */
    private void mutate(String description, Runnable change) {
        String snapshot = service.write(doc, false);
        try {
            change.run();
            doc = service.normalise(doc);
            undo.push(snapshot);
            if (undo.size() > 100) {
                undo.removeLast();
            }
            dirty = true;
            refresh();
            status(description + ".");
        } catch (RuntimeException e) {
            doc = service.read(snapshot).document();
            refresh();
            error("Change not applied", e);
        }
    }

    // ---- selection & rendering ------------------------------------------------------------

    void select(ACHRecord record, boolean showRecordTab) {
        selected = record;
        syncing = true;
        try {
            selectInTree(tree.getRoot(), record);
            rawList.getSelectionModel().select(record);
            rawList.scrollTo(Math.max(0, rawList.getSelectionModel().getSelectedIndex() - 3));
        } finally {
            syncing = false;
        }
        recordScroll.setContent(new RecordForm(service, record, describe(record), this::applyEdits).build());
        if (showRecordTab) {
            tabs.getSelectionModel().select(recordTab);
        }
    }

    private boolean selectInTree(TreeItem<Node> item, ACHRecord record) {
        if (item == null) {
            return false;
        }
        if (item.getValue() != null && item.getValue().record() == record) {
            tree.getSelectionModel().select(item);
            int row = tree.getRow(item);
            if (row >= 0) {
                tree.scrollTo(Math.max(0, row - 3));
            }
            return true;
        }
        for (TreeItem<Node> child : item.getChildren()) {
            if (selectInTree(child, record)) {
                item.setExpanded(true);
                return true;
            }
        }
        return false;
    }

    private void refresh() {
        updateTitle();
        if (doc == null) {
            tree.setRoot(null);
            overviewScroll.setContent(OverviewView.empty());
            recordScroll.setContent(null);
            rawList.getItems().clear();
            issuesList.getItems().clear();
            statusBadge.setText("");
            return;
        }
        List<AchValidator.Issue> issues = AchValidator.validate(doc);
        syncing = true;
        try {
            tree.setRoot(buildTree());
            rawList.getItems().setAll(AchService.records(doc));
            issuesList.getItems().setAll(issues);
        } finally {
            syncing = false;
        }
        long errors = issues.stream().filter(i -> i.severity() == AchValidator.Severity.ERROR).count();
        long warnings = issues.size() - errors;
        statusBadge.getStyleClass().removeAll("badge-ok", "badge-error", "badge-warn");
        if (issues.isEmpty()) {
            statusBadge.setText("✔ Valid file");
            statusBadge.getStyleClass().add("badge-ok");
        } else if (errors > 0) {
            statusBadge.setText("✖ " + errors + " error(s)" + (warnings > 0 ? ", " + warnings + " warning(s)" : ""));
            statusBadge.getStyleClass().add("badge-error");
        } else {
            statusBadge.setText("⚠ " + warnings + " warning(s)");
            statusBadge.getStyleClass().add("badge-warn");
        }
        issuesTitle.setText(issues.isEmpty() ? "VALIDATION · no problems found" : "VALIDATION · " + issues.size() + " issue(s) – click to jump to the record");

        overviewScroll.setContent(new OverviewView(service, doc, readNotes, issues, r -> select(r, true)).build());
        if (selected == null || !AchService.records(doc).contains(selected)) {
            selected = doc.getFileHeader();
        }
        select(selected, false);
    }

    private TreeItem<Node> buildTree() {
        TreeItem<Node> top = new TreeItem<>(new Node("root", null, "node-root"));
        AchSummary.FileInfo info = AchSummary.file(doc);
        TreeItem<Node> header = new TreeItem<>(new Node("File header · " + info.originName() + " → " + info.destinationName(),
                doc.getFileHeader(), "node-file"));
        top.getChildren().add(header);
        for (ACHBatch batch : doc.getBatches()) {
            AchSummary.BatchInfo b = AchSummary.batch(batch);
            TreeItem<Node> bi = new TreeItem<>(new Node(b.title() + " · " + b.entries() + " entr" + (b.entries() == 1 ? "y" : "ies"),
                    batch.getBatchHeader(), "node-batch"));
            bi.setExpanded(true);
            for (ACHBatchDetail d : batch.getDetails()) {
                AchSummary.EntryInfo e = AchSummary.entry(service, d);
                TreeItem<Node> ei = new TreeItem<>(new Node(e.direction() + " " + AchFormat.money(e.amount()) + " · " + e.name(),
                        d.getDetailRecord(), e.direction().toLowerCase().contains("debit") ? "node-debit" : "node-credit"));
                for (AddendaRecord a : d.getAddendaRecords()) {
                    ei.getChildren().add(new TreeItem<>(new Node("Addenda " + a.getAddendaTypeCode() + " · " + AchSummary.addenda(a), a, "node-addenda")));
                }
                bi.getChildren().add(ei);
            }
            if (batch.getBatchControl() != null) {
                bi.getChildren().add(new TreeItem<>(new Node("Batch control · credits " + AchFormat.money(b.credits())
                        + " · debits " + AchFormat.money(b.debits()), batch.getBatchControl(), "node-control")));
            }
            top.getChildren().add(bi);
        }
        top.getChildren().add(new TreeItem<>(new Node("File control · " + info.batches() + " batch(es) · " + info.entries() + " entries",
                doc.getFileControl(), "node-file")));
        return top;
    }

    private String describe(ACHRecord record) {
        if (record == null) {
            return "";
        }
        if (record instanceof com.afrunt.jach.domain.EntryDetail) {
            for (ACHBatch batch : doc.getBatches()) {
                for (ACHBatchDetail d : batch.getDetails()) {
                    if (d.getDetailRecord() == record) {
                        AchSummary.EntryInfo e = AchSummary.entry(service, d);
                        return e.direction() + " of " + AchFormat.money(e.amount()) + " · " + e.name() + " · "
                                + e.accountType() + " account " + e.account() + " at routing " + e.routing();
                    }
                }
            }
        }
        if (record instanceof com.afrunt.jach.domain.BatchHeader bh) {
            for (ACHBatch batch : doc.getBatches()) {
                if (batch.getBatchHeader() == bh) {
                    AchSummary.BatchInfo b = AchSummary.batch(batch);
                    return b.title() + " · " + b.secMeaning() + " · effective " + b.effectiveDate();
                }
            }
        }
        if (record instanceof AddendaRecord a) {
            return AchCodes.describe(AchCodes.ADDENDA_TYPE, a.getAddendaTypeCode()) + " · " + AchSummary.addenda(a);
        }
        return AchService.recordTypeLabel(record);
    }

    private ACHBatch selectedBatch() {
        if (doc == null || selected == null) {
            return null;
        }
        for (ACHBatch batch : doc.getBatches()) {
            if (AchService.records(batch).contains(selected)) {
                return batch;
            }
        }
        return null;
    }

    private ACHBatchDetail selectedDetail() {
        if (doc == null || selected == null) {
            return null;
        }
        for (ACHBatch batch : doc.getBatches()) {
            for (ACHBatchDetail d : batch.getDetails()) {
                if (d.getDetailRecord() == selected || d.getAddendaRecords().contains(selected)) {
                    return d;
                }
            }
        }
        return null;
    }

    private int selectedIndex() {
        return doc == null ? 0 : Math.max(0, AchService.records(doc).indexOf(selected));
    }

    private ACHRecord recordAt(int index) {
        List<ACHRecord> records = AchService.records(doc);
        return records.isEmpty() ? null : records.get(Math.min(index, records.size() - 1));
    }

    // ---- helpers ---------------------------------------------------------------------------

    private FileChooser chooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("ACH / NACHA files", "*.ach", "*.txt", "*.nacha", "*.dat"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        Prefs.lastDir().ifPresent(d -> chooser.setInitialDirectory(d.toFile()));
        return chooser;
    }

    private boolean confirmDiscard() {
        if (doc == null || !dirty) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "You have unsaved changes. Discard them?",
                ButtonType.YES, ButtonType.NO);
        alert.initOwner(stage);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void updateTitle() {
        String name = doc == null ? "" : " — " + (file == null ? "Untitled" : file.getFileName()) + (dirty ? " *" : "");
        stage.setTitle("ACH Studio" + name);
    }

    private void status(String message) {
        statusLabel.setText(message);
    }

    private void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.initOwner(stage);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void error(String title, Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR, e.getMessage() == null ? e.toString() : e.getMessage(), ButtonType.OK);
        alert.initOwner(stage);
        alert.setHeaderText(title);
        alert.getDialogPane().setMinWidth(520);
        alert.showAndWait();
        status(title + ": " + e.getMessage());
    }

    private static String rulerText() {
        StringBuilder tens = new StringBuilder("      ");
        StringBuilder units = new StringBuilder("      ");
        for (int i = 1; i <= AchService.RECORD_LENGTH; i++) {
            tens.append(i % 10 == 0 ? String.valueOf((i / 10) % 10) : " ");
            units.append(i % 10);
        }
        return tens + "\n" + units;
    }
}
