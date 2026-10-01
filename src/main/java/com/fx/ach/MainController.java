package com.fx.ach;

import com.afrunt.jach.document.ACHBatch;
import com.afrunt.jach.document.ACHBatchDetail;
import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.ACHRecord;
import com.afrunt.jach.domain.AddendaRecord;
import com.afrunt.jach.domain.BatchControl;
import com.afrunt.jach.domain.BatchHeader;
import com.afrunt.jach.domain.EntryDetail;
import com.afrunt.jach.domain.FileControl;
import com.fx.ach.core.AchBuilder;
import com.fx.ach.core.AchCodes;
import com.fx.ach.core.AchControls;
import com.fx.ach.core.AchException;
import com.fx.ach.core.AchFormat;
import com.fx.ach.core.AchInsert;
import com.fx.ach.core.AchInsert.Kind;
import com.fx.ach.core.AchInsert.Slot;
import com.fx.ach.core.AchInsert.Where;
import com.fx.ach.core.AchReport;
import com.fx.ach.core.AchService;
import com.fx.ach.core.AchSummary;
import com.fx.ach.core.AchTemplates;
import com.fx.ach.core.AchValidator;
import com.fx.ach.core.FieldView;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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
import java.util.function.Supplier;

public class MainController {

    @FXML private BorderPane root;
    @FXML private Menu newMenu;
    @FXML private MenuButton newButton;
    @FXML private Label statusBadge;
    @FXML private ToggleButton presentToggle;
    @FXML private ToggleButton editModeToggle;
    @FXML private SplitPane mainSplit;
    @FXML private TabPane tabs;
    @FXML private StackPane fileStack;
    @FXML private VBox rawPane;
    @FXML private ScrollPane overviewScroll;
    @FXML private ListView<ACHRecord> rawList;
    @FXML private HBox ruler;
    @FXML private ListView<AchValidator.Issue> issuesList;
    @FXML private Label issuesTitle;
    @FXML private Label statusLabel;

    private final AchService service = new AchService();
    private final Deque<String> undo = new ArrayDeque<>();
    private RecordPanel recordPanel;
    private FormView formView;
    private boolean formViewStale = true;
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

        recordPanel = new RecordPanel(service, this::applyEdits, this::rawAction);
        recordPanel.setMinWidth(320);
        mainSplit.getItems().add(recordPanel);
        mainSplit.setDividerPositions(0.66);
        SplitPane.setResizableWithParent(recordPanel, false);

        formView = new FormView(service, r -> requestSelect(r, false), this::contextMenu);
        formView.setVisible(false);
        fileStack.getChildren().add(formView);
        presentToggle.selectedProperty().addListener((o, was, present) -> {
            rawPane.setVisible(!present);
            formView.setVisible(present);
            if (present && formViewStale) {
                formView.show(doc);
                formViewStale = false;
            }
            if (selected != null) {
                showRecord(selected, true);
            }
        });
        editModeToggle.selectedProperty().addListener((o, was, on) -> {
            recordPanel.setEditMode(on);
            status(on ? "Edit mode on: field changes are applied automatically when you move to another record."
                    : "Edit mode off: you'll be asked before unapplied changes are lost.");
        });

        rawList.setCellFactory(l -> new RawCell());
        rawList.getSelectionModel().selectedItemProperty().addListener((o, previous, r) -> {
            if (!syncing && r != null && r != selected && !requestSelect(r, false)) {
                syncing = true;
                rawList.getSelectionModel().select(selected);
                syncing = false;
            }
        });
        rawList.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                onDelete();
            }
        });
        buildRuler();

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
        issuesList.setOnMouseClicked(e -> {
            AchValidator.Issue issue = issuesList.getSelectionModel().getSelectedItem();
            if (issue != null && issue.record() != null) {
                tabs.getSelectionModel().select(0);
                requestSelect(issue.record(), true);
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

    // ---- raw view ----------------------------------------------------------------------------

    /** One raw record, every field shaded in its own colour with a tooltip naming it. */
    private final class RawCell extends ListCell<ACHRecord> {
        RawCell() {
            setOnContextMenuRequested(e -> {
                ACHRecord r = getItem();
                if (r != null && requestSelect(r, false)) {
                    contextMenu(selected).show(this, e.getScreenX(), e.getScreenY());
                }
                e.consume();
            });
        }

        @Override
        protected void updateItem(ACHRecord r, boolean empty) {
            super.updateItem(r, empty);
            setText(null);
            if (empty || r == null) {
                setGraphic(null);
                return;
            }
            Label number = new Label(String.format("%4d  ", r.getLineNumber()));
            number.getStyleClass().addAll("line-number", "rt-" + r.getRecordTypeCode());
            HBox line = new HBox(number);
            line.setAlignment(Pos.CENTER_LEFT);
            List<FieldView> fields = service.fields(r);
            for (int i = 0; i < fields.size(); i++) {
                FieldView f = fields.get(i);
                Label seg = FieldColors.segment(f, i);
                seg.setOnMouseClicked(e -> {
                    if (selected == r && e.getClickCount() == 1) {
                        recordPanel.focusField(f.start());
                    }
                });
                line.getChildren().add(seg);
            }
            setGraphic(line);
        }
    }

    // ---- selection ---------------------------------------------------------------------------

    /**
     * Moves the selection to {@code target}, first dealing with unapplied edits in the record panel:
     * applied silently in edit mode, otherwise the user chooses. Returns false if they cancelled.
     */
    boolean requestSelect(ACHRecord target, boolean scroll) {
        if (target == null) {
            return false;
        }
        if (recordPanel.isDirty() && recordPanel.record() != target) {
            int targetIndex = indexOf(target);
            if (!resolvePendingEdits()) {
                return false;
            }
            target = recordAt(targetIndex);
        }
        showRecord(target, scroll);
        return true;
    }

    private void showRecord(ACHRecord record, boolean scroll) {
        selected = record;
        recordPanel.show(record, describe(record));
        syncing = true;
        try {
            rawList.getSelectionModel().select(record);
            if (scroll) {
                rawList.scrollTo(Math.max(0, rawList.getSelectionModel().getSelectedIndex() - 3));
            }
        } finally {
            syncing = false;
        }
        if (formView.isVisible()) {
            formView.reveal(record);
        }
    }

    /** Returns true when there are no unapplied edits left (applied or discarded). */
    private boolean resolvePendingEdits() {
        if (!recordPanel.isDirty()) {
            return true;
        }
        if (editModeToggle.isSelected()) {
            return applyEdits(recordPanel.record(), recordPanel.edits());
        }
        ButtonType apply = new ButtonType("Apply changes", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "You changed " + recordPanel.edits().size() + " field(s) of this record without applying them.\n"
                        + "Tip: turn on Edit mode to apply changes automatically.", apply, discard, ButtonType.CANCEL);
        alert.initOwner(stage);
        alert.setHeaderText("Apply your changes?");
        ButtonType choice = alert.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == apply) {
            return applyEdits(recordPanel.record(), recordPanel.edits());
        }
        if (choice == discard) {
            recordPanel.show(recordPanel.record(), describe(recordPanel.record()));
            return true;
        }
        return false;
    }

    // ---- context menu: insert before / after ------------------------------------------------

    ContextMenu contextMenu(ACHRecord record) {
        ContextMenu menu = new ContextMenu();
        if (doc == null || record == null) {
            return menu;
        }
        for (Where where : Where.values()) {
            Menu sub = new Menu(where == Where.BEFORE ? "Insert before" : "Insert after");
            for (Kind kind : Kind.values()) {
                Optional<Slot> slot = AchInsert.slot(doc, record, kind, where);
                MenuItem item = new MenuItem(insertLabel(record, kind, where));
                if (slot.isEmpty()) {
                    item.setText(item.getText() + "  (not allowed here)");
                    item.setDisable(true);
                } else {
                    item.setOnAction(e -> insert(slot.get(), batchTemplate(record)));
                }
                sub.getItems().add(item);
            }
            menu.getItems().add(sub);
        }
        MenuItem delete = new MenuItem("Delete this record");
        delete.setDisable(record == doc.getFileHeader() || record == doc.getFileControl());
        delete.setOnAction(e -> onDelete());
        menu.getItems().addAll(new SeparatorMenuItem(), delete);
        return menu;
    }

    private String insertLabel(ACHRecord anchor, Kind kind, Where where) {
        boolean insideBatch = !(anchor == doc.getFileHeader() || anchor == doc.getFileControl());
        return switch (kind) {
            case BATCH -> insideBatch ? "Batch… (" + (where == Where.BEFORE ? "before" : "after") + " this whole batch)" : "Batch…";
            case ENTRY -> "Entry…";
            case ADDENDA -> "Addenda (remittance note)…";
        };
    }

    private BatchHeader batchTemplate(ACHRecord anchor) {
        for (ACHBatch batch : doc.getBatches()) {
            if (AchService.records(batch).contains(anchor)) {
                return batch.getBatchHeader();
            }
        }
        return doc.getBatches().isEmpty() ? null : doc.getBatches().get(doc.getBatches().size() - 1).getBatchHeader();
    }

    /** Asks for the new record's details (form or pasted raw lines), then inserts it at the slot. */
    private void insert(Slot slot, BatchHeader templateHeader) {
        if (!resolvePendingEdits()) {
            return;
        }
        Supplier<ACHRecord> inserted = () -> AchInsert.recordAt(doc, slot);
        switch (slot.kind()) {
            case BATCH -> Dialogs.batch(stage, templateHeader).ifPresent(r -> {
                if (r.isRaw()) {
                    insertRaw(slot, r.raw());
                } else {
                    commit("Inserted batch", () -> {
                        r.built().setBatchControl(new BatchControl());
                        AchInsert.insert(doc, slot, r.built());
                        AchControls.recalculate(doc);
                    }, inserted);
                }
            });
            case ENTRY -> {
                String sec = AchFormat.trim(AchInsert.batchOf(doc, slot).getBatchHeader().getStandardEntryClassCode());
                Dialogs.entry(stage, sec).ifPresent(r -> {
                    if (r.isRaw()) {
                        insertRaw(slot, r.raw());
                    } else {
                        commit("Inserted entry", () -> {
                            AchInsert.insert(doc, slot, r.built());
                            AchBuilder.finish(doc);
                        }, inserted);
                    }
                });
            }
            case ADDENDA -> Dialogs.addenda(stage).ifPresent(r -> {
                if (r.isRaw()) {
                    insertRaw(slot, r.raw());
                } else {
                    commit("Inserted addenda", () -> {
                        AchInsert.insert(doc, slot, AchBuilder.newAddenda(r.built()));
                        AchBuilder.finish(doc);
                    }, inserted);
                }
            });
        }
    }

    private void insertRaw(Slot slot, String raw) {
        int at = AchInsert.lineIndex(doc, slot);
        splice("Inserted pasted line(s)", at, 0, raw);
    }

    /** Raw-line box in the record panel: replace the record, or insert pasted line(s) next to it. */
    private void rawAction(RecordPanel.RawAction action, ACHRecord record, String text) {
        int index = indexOf(record);
        if (!resolvePendingEdits()) {
            return;
        }
        switch (action) {
            case REPLACE -> splice("Replaced record from raw line", index, 1, text);
            case BEFORE -> splice("Inserted pasted line(s) before", index, 0, text);
            case AFTER -> splice("Inserted pasted line(s) after", index + 1, 0, text);
        }
    }

    /**
     * Swaps raw lines into the file text and re-parses it. Totals are recalculated afterwards unless
     * the pasted lines are themselves control records (8/9), which are kept exactly as pasted.
     */
    private void splice(String description, int at, int remove, String text) {
        boolean controlsOnly;
        try {
            controlsOnly = AchService.pastedLines(text).stream().allMatch(l -> l.startsWith("8") || l.startsWith("9"));
        } catch (AchException e) {
            error("Can't use the pasted text", e);
            return;
        }
        commit(description, () -> {
            doc = service.splice(doc, at, remove, text);
            if (!controlsOnly) {
                AchControls.recalculate(doc);
            }
        }, () -> recordAt(at));
    }

    /** Inserts after the selection when possible, otherwise before it (toolbar and Edit menu). */
    private void insertNearSelection(Kind kind) {
        if (doc == null || !resolvePendingEdits()) {
            return;
        }
        ACHRecord anchor = selected == null ? doc.getFileControl() : selected;
        if (kind == Kind.ADDENDA && anchor instanceof EntryDetail) {
            ACHBatchDetail d = detailOf(anchor);
            if (d != null && !d.getAddendaRecords().isEmpty()) {
                anchor = d.getAddendaRecords().get(d.getAddendaRecords().size() - 1);
            }
        }
        if (kind == Kind.ENTRY && (anchor == doc.getFileHeader() || anchor == doc.getFileControl()) && doc.getBatches().size() == 1) {
            anchor = doc.getBatches().get(0).getBatchControl();
        }
        Optional<Slot> slot = AchInsert.slot(doc, anchor, kind, Where.AFTER);
        if (slot.isEmpty()) {
            slot = AchInsert.slot(doc, anchor, kind, Where.BEFORE);
        }
        if (slot.isEmpty()) {
            info(switch (kind) {
                case ENTRY -> "Select a batch, entry or batch control first – the new entry goes next to it.";
                case ADDENDA -> "Select an entry or addenda first.";
                case BATCH -> "Select where the new batch should go.";
            });
            return;
        }
        insert(slot.get(), batchTemplate(anchor));
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
    private void onNewFromPaste() {
        if (!confirmDiscard()) {
            return;
        }
        Dialogs.pastedFile(stage).ifPresent(text -> {
            try {
                AchService.ReadResult result = service.read(text);
                load(result.document(), null, result.notes(), "New file from pasted text.");
            } catch (AchException e) {
                error("Could not read the pasted text", e);
            }
        });
    }

    @FXML
    private void onOpen() {
        if (!confirmDiscard()) {
            return;
        }
        File f = chooser("Open ACH file").showOpenDialog(stage);
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
        recordPanel.show(null, "");
        refresh();
        tabs.getSelectionModel().select(0);
        status(status);
    }

    @FXML
    private void onSave() {
        if (doc == null || !resolvePendingEdits()) {
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
        if (doc == null || !resolvePendingEdits()) {
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
                commit("Recalculated controls", () -> AchControls.recalculate(doc), null);
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
        if (doc == null || !resolvePendingEdits()) {
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
        if (doc == null || !resolvePendingEdits()) {
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
        if (recordPanel.isDirty()) {
            recordPanel.show(recordPanel.record(), describe(recordPanel.record()));
            status("Unapplied field changes discarded.");
            return;
        }
        if (undo.isEmpty()) {
            status("Nothing to undo.");
            return;
        }
        int index = indexOf(selected);
        doc = service.read(undo.pop()).document();
        dirty = true;
        selected = recordAt(index);
        refresh();
        status("Undone.");
    }

    @FXML
    private void onAddBatch() {
        insertNearSelection(Kind.BATCH);
    }

    @FXML
    private void onAddEntry() {
        insertNearSelection(Kind.ENTRY);
    }

    @FXML
    private void onAddAddenda() {
        insertNearSelection(Kind.ADDENDA);
    }

    @FXML
    private void onDelete() {
        if (doc == null || selected == null || !resolvePendingEdits()) {
            return;
        }
        String what = describe(selected);
        if (selected == doc.getFileHeader() || selected == doc.getFileControl()) {
            info("The file header and file control can't be deleted.");
            return;
        }
        boolean wholeBatch = selected instanceof BatchHeader || selected instanceof BatchControl;
        if (!editModeToggle.isSelected()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete " + (wholeBatch ? "this whole batch" : what)
                    + "?\n\nTip: in Edit mode deletes happen without asking (Ctrl+Z undoes).", ButtonType.OK, ButtonType.CANCEL);
            confirm.initOwner(stage);
            confirm.setHeaderText(null);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }
        ACHRecord target = selected;
        int index = indexOf(target);
        commit("Deleted " + (wholeBatch ? "batch" : what) + " (Ctrl+Z to undo)", () -> {
            for (ACHBatch batch : new ArrayList<>(doc.getBatches())) {
                if (batch.getBatchHeader() == target || batch.getBatchControl() == target) {
                    doc.getBatches().remove(batch);
                    break;
                }
                for (ACHBatchDetail d : new ArrayList<>(batch.getDetails())) {
                    if (d.getDetailRecord() == target) {
                        batch.getDetails().remove(d);
                    } else {
                        d.getAddendaRecords().remove(target);
                    }
                }
            }
            AchBuilder.finish(doc);
        }, () -> recordAt(Math.max(0, index - 1)));
    }

    @FXML
    private void onRecalculate() {
        if (doc != null && resolvePendingEdits()) {
            commit("Recalculated batch and file control totals", () -> AchControls.recalculate(doc), null);
        }
    }

    @FXML
    private void onRenumber() {
        if (doc != null && resolvePendingEdits()) {
            commit("Renumbered trace numbers", () -> AchBuilder.finish(doc), null);
        }
    }

    /** Applies field edits from the record panel. Returns false if they were rejected. */
    boolean applyEdits(ACHRecord record, Map<FieldView, String> edits) {
        if (edits.isEmpty()) {
            recordPanel.show(record, describe(record));
            return true;
        }
        boolean isControl = record instanceof BatchControl || record instanceof FileControl;
        return commit("Updated " + edits.size() + " field(s)", () -> {
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
        }, null);
    }

    /**
     * Runs a change against the document, then round-trips it through text so every record stays
     * consistent. On failure the previous state is restored. {@code nextSelection} (evaluated after
     * the change) picks the record to show; null keeps the same position.
     */
    private boolean commit(String description, Runnable change, Supplier<ACHRecord> nextSelection) {
        String snapshot = service.write(doc, false);
        int index = indexOf(selected);
        try {
            change.run();
            doc = service.normalise(doc);
            undo.push(snapshot);
            if (undo.size() > 100) {
                undo.removeLast();
            }
            dirty = true;
            selected = nextSelection == null ? recordAt(index) : nextSelection.get();
            recordPanel.show(null, "");
            refresh();
            status(description + ".");
            return true;
        } catch (RuntimeException e) {
            doc = service.read(snapshot).document();
            selected = recordAt(index);
            refresh();
            error("Change not applied", e);
            return false;
        }
    }

    // ---- rendering ---------------------------------------------------------------------------

    private void refresh() {
        updateTitle();
        formViewStale = true;
        if (doc == null) {
            overviewScroll.setContent(OverviewView.empty());
            rawList.getItems().clear();
            issuesList.getItems().clear();
            formView.show(null);
            statusBadge.setText("");
            return;
        }
        List<AchValidator.Issue> issues = AchValidator.validate(doc);
        syncing = true;
        try {
            rawList.getItems().setAll(AchService.records(doc));
            issuesList.getItems().setAll(issues);
        } finally {
            syncing = false;
        }
        if (formView.isVisible()) {
            formView.show(doc);
            formViewStale = false;
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

        overviewScroll.setContent(new OverviewView(service, doc, readNotes, issues, r -> requestSelect(r, true)).build());
        if (selected == null || !AchService.records(doc).contains(selected)) {
            selected = doc.getFileHeader();
        }
        showRecord(selected, true);
    }

    private String describe(ACHRecord record) {
        if (record == null || doc == null) {
            return "";
        }
        if (record instanceof EntryDetail) {
            ACHBatchDetail d = detailOf(record);
            if (d != null) {
                AchSummary.EntryInfo e = AchSummary.entry(service, d);
                return e.direction() + " of " + AchFormat.money(e.amount()) + " · " + e.name() + " · "
                        + e.accountType() + " account " + e.account() + " at routing " + e.routing();
            }
        }
        if (record instanceof BatchHeader bh) {
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

    private ACHBatchDetail detailOf(ACHRecord record) {
        for (ACHBatch batch : doc.getBatches()) {
            for (ACHBatchDetail d : batch.getDetails()) {
                if (d.getDetailRecord() == record || d.getAddendaRecords().contains(record)) {
                    return d;
                }
            }
        }
        return null;
    }

    private int indexOf(ACHRecord record) {
        return doc == null ? 0 : Math.max(0, AchService.records(doc).indexOf(record));
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
        if (!resolvePendingEdits()) {
            return false;
        }
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

    /** Text colours for the column ruler, one per block of ten columns. */
    private static final String[] RULER_COLORS = {
            "#2457c5", "#0f7b78", "#1a7f37", "#6f7a12", "#b35c00", "#b42318", "#a3238e", "#6b3fa0", "#3f51b5", "#7a4b2a"
    };

    /** Column ruler above the raw lines, colour-coded in blocks of ten columns (1–10, 11–20, …). */
    private void buildRuler() {
        Label gutter = new Label("      \n      ");
        gutter.getStyleClass().add("ruler-block");
        ruler.getChildren().setAll(gutter);
        for (int start = 1; start <= AchService.RECORD_LENGTH; start += 10) {
            int end = Math.min(start + 9, AchService.RECORD_LENGTH);
            // top row labels the block with its first column; bottom row counts 1..0 within it
            String label = String.valueOf(start);
            StringBuilder units = new StringBuilder();
            for (int i = start; i <= end; i++) {
                units.append(i % 10);
            }
            String top = label + " ".repeat(units.length() - label.length());
            Label block = new Label(top + "\n" + units);
            int n = (start - 1) / 10;
            block.getStyleClass().addAll("ruler-block", n % 2 == 0 ? "ruler-even" : "ruler-odd");
            block.setStyle("-fx-text-fill: " + RULER_COLORS[n % RULER_COLORS.length] + ";");
            block.setMinWidth(Region.USE_PREF_SIZE);
            block.setTooltip(new Tooltip("Columns " + start + "–" + end));
            ruler.getChildren().add(block);
        }
    }
}
