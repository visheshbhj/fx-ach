package com.fx.ach;

import com.afrunt.jach.document.ACHBatch;
import com.afrunt.jach.document.ACHBatchDetail;
import com.afrunt.jach.document.ACHDocument;
import com.afrunt.jach.domain.BatchHeader;
import com.afrunt.jach.domain.GeneralBatchHeader;
import com.fx.ach.core.AchBuilder;
import com.fx.ach.core.AchBuilder.AccountType;
import com.fx.ach.core.AchCodes;
import com.fx.ach.core.AchException;
import com.fx.ach.core.AchFormat;
import com.fx.ach.core.AchTemplates;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Forms for creating files, batches and entries without touching fixed-width text.
 */
final class Dialogs {

    record TemplateOptions(LocalDate effectiveDate, boolean clearAmounts) {
    }

    /** What an "add" dialog produced: either a record built from the form, or pasted raw line(s). */
    record Result<T>(T built, String raw) {
        boolean isRaw() {
            return raw != null;
        }
    }

    private Dialogs() {
    }

    // ---- new file ------------------------------------------------------------------------

    static Optional<ACHDocument> newFile(Window owner, AchTemplates.BuiltIn template) {
        AchTemplates.Origin o = Prefs.origin();
        Form form = new Form("New file – " + template.title,
                "Who is sending this file, and to which bank? These details go into the file header and batch header. "
                        + "Your answers are remembered for next time.");
        TextField destRouting = form.text("Destination bank routing", o.file().destinationRouting(), 9,
                "9-digit routing number of the bank / ACH operator receiving the file");
        TextField destName = form.text("Destination bank name", o.file().destinationName(), 23, null);
        TextField origin = form.text("Origin (routing or ID)", o.file().origin(), 10,
                "Usually your bank's routing number or a 10-digit company ID your bank gave you");
        TextField originName = form.text("Origin name", o.file().originName(), 23, null);
        TextField fileId = form.text("File ID modifier", "A", 1, "A for the first file today, B for the second…");
        form.section("Originator (appears on receivers' statements)");
        TextField companyName = form.text("Company name", o.companyName(), 16, null);
        TextField companyId = form.text("Company ID", o.companyId(), 10, "Often 1 followed by your EIN");
        TextField odfi = form.text("Your bank's routing (ODFI)", o.odfiRouting(), 9, "Prefixes every trace number");
        DatePicker effective = form.date("Effective entry date", o.effectiveDate(), "When the money should settle");
        form.routingCheck(destRouting);
        form.routingCheck(odfi);

        return form.show(owner, () -> {
            AchTemplates.Origin chosen = new AchTemplates.Origin(
                    new AchBuilder.FileSettings(destRouting.getText(), destName.getText(), origin.getText(),
                            originName.getText(), fileId.getText()),
                    companyName.getText(), companyId.getText(), odfi.getText(), effective.getValue());
            ACHDocument doc = AchTemplates.create(template, chosen);
            Prefs.setOrigin(chosen);
            return doc;
        });
    }

    static Optional<TemplateOptions> userTemplate(Window owner, Path template) {
        Form form = new Form("New file from " + template.getFileName(),
                "The new file gets today's creation date and time. Batches, receivers and accounts are copied from the template.");
        DatePicker effective = form.date("Effective entry date", AchFormat.nextBusinessDay(LocalDate.now()), null);
        CheckBox clear = form.check("Set all amounts to $0.00 so I can fill them in", false);
        return form.show(owner, () -> new TemplateOptions(effective.getValue(), clear.isSelected()));
    }

    // ---- batch -------------------------------------------------------------------------------

    static Optional<Result<ACHBatch>> batch(Window owner, BatchHeader like) {
        GeneralBatchHeader g = like instanceof GeneralBatchHeader gb ? gb : null;
        Form form = new Form("Add batch", "A batch groups entries of one type from one company with one effective date.");
        form.rawPaste("Paste a batch header (5), optionally with its entries/addenda (6/7) and batch control (8). "
                + "Any SEC code works this way, including IAT.");
        ComboBox<String> sec = form.combo("Entry type (SEC code)", AchBuilder.BUILDABLE_SEC,
                g == null ? "PPD" : AchFormat.trim(g.getStandardEntryClassCode()));
        Label secHint = form.hint(AchCodes.describe(AchCodes.SEC, sec.getValue()));
        sec.valueProperty().addListener((obs, a, v) -> secHint.setText(AchCodes.describe(AchCodes.SEC, v)));
        if (!AchBuilder.BUILDABLE_SEC.contains(sec.getValue())) {
            sec.setValue("PPD");
        }
        TextField company = form.text("Company name", g == null ? Prefs.origin().companyName() : AchFormat.trim(g.getCompanyName()), 16, null);
        TextField companyId = form.text("Company ID", g == null ? Prefs.origin().companyId() : AchFormat.trim(g.getCompanyID()), 10, null);
        TextField description = form.text("Entry description", g == null ? "PAYMENT" : AchFormat.trim(g.getCompanyEntryDescription()), 10,
                "Shown on statements, e.g. PAYROLL, INVOICE, REFUND");
        TextField descriptive = form.text("Descriptive date (optional)", "", 6, "Free text, e.g. SEP 26");
        TextField discretionary = form.text("Discretionary data (optional)", "", 20, null);
        DatePicker effective = form.date("Effective entry date", AchFormat.nextBusinessDay(LocalDate.now()), null);
        TextField odfi = form.text("Your bank's routing (ODFI)", g == null ? Prefs.origin().odfiRouting()
                : AchFormat.trim(g.getOriginatorDFIIdentifier()), 9, "8 or 9 digits");
        return form.showWithRaw(owner, () -> AchBuilder.newBatch(new AchBuilder.BatchSettings(sec.getValue(), company.getText(),
                companyId.getText(), description.getText(), effective.getValue(), odfi.getText(),
                discretionary.getText(), descriptive.getText())));
    }

    // ---- entry -------------------------------------------------------------------------------

    static Optional<Result<ACHBatchDetail>> entry(Window owner, String sec) {
        boolean corporate = "CCD".equals(sec);
        Form form = new Form("Add " + sec + " entry", AchCodes.describe(AchCodes.SEC, sec));
        form.rawPaste("Paste an entry detail (6) and, optionally, its addenda (7).");
        if (!AchBuilder.BUILDABLE_SEC.contains(sec)) {
            form.hint("The form below supports PPD, CCD, WEB and TEL; for " + sec + " paste the raw lines above.");
        }
        TextField name = form.text(corporate ? "Receiving company" : "Receiver name", "", 22, null);
        TextField id = form.text("Identification number", "", 15,
                "WEB".equals(sec) || "TEL".equals(sec) ? "Required: your reference for this customer" : "Optional reference, e.g. employee number");
        TextField routing = form.text("Bank routing number", "", 9, null);
        form.routingCheck(routing);
        TextField account = form.text("Account number", "", 17, null);
        ComboBox<AccountType> type = form.combo("Account type", java.util.List.of(AccountType.values()), AccountType.CHECKING);

        ToggleGroup direction = new ToggleGroup();
        RadioButton credit = new RadioButton("Credit – pay the receiver");
        RadioButton debit = new RadioButton("Debit – collect from the receiver");
        credit.setToggleGroup(direction);
        debit.setToggleGroup(direction);
        (("WEB".equals(sec) || "TEL".equals(sec)) ? debit : credit).setSelected(true);
        form.row("Direction", new VBox(4, credit, debit));
        CheckBox prenote = form.check("Prenote (zero-dollar test entry to verify the account)", false);
        TextField amount = form.text("Amount ($)", "", 12, "e.g. 1250.00");
        prenote.selectedProperty().addListener((obs, a, v) -> amount.setDisable(v));

        ComboBox<String> paymentType = null;
        if ("WEB".equals(sec) || "TEL".equals(sec)) {
            paymentType = form.combo("Payment type", java.util.List.of("S", "R"), "S");
            form.hint("S = single payment, R = recurring");
        }
        TextField addenda = form.text("Remittance note (addenda, optional)", "", 80, "e.g. INV 10045 – sent as an 05 addenda record");
        if ("TEL".equals(sec)) {
            addenda.setDisable(true);
        }

        Label preview = form.hint("");
        preview.getStyleClass().add("preview");
        Runnable update = () -> {
            try {
                int code = AchBuilder.transactionCode(type.getValue(), credit.isSelected(), prenote.isSelected());
                String amt = prenote.isSelected() ? "$0.00 prenote" : amount.getText().isBlank() ? "$—" : "$" + amount.getText().trim();
                preview.setText("Transaction code " + code + " (" + AchCodes.describe(AchCodes.TRANSACTION, String.valueOf(code))
                        + ") · " + amt + (credit.isSelected() ? " to " : " from ") + (name.getText().isBlank() ? "receiver" : name.getText()));
            } catch (AchException e) {
                preview.setText(e.getMessage());
            }
        };
        name.textProperty().addListener(o -> update.run());
        amount.textProperty().addListener(o -> update.run());
        type.valueProperty().addListener(o -> update.run());
        direction.selectedToggleProperty().addListener(o -> update.run());
        prenote.selectedProperty().addListener(o -> update.run());
        update.run();

        ComboBox<String> pt = paymentType;
        return form.showWithRaw(owner, () -> {
            BigDecimal value;
            if (prenote.isSelected()) {
                value = BigDecimal.ZERO;
            } else {
                try {
                    value = new BigDecimal(amount.getText().trim().replace(",", "").replace("$", ""));
                } catch (NumberFormatException e) {
                    throw new AchException("Enter the amount as a number, e.g. 1250.00");
                }
            }
            return AchBuilder.newEntry(sec, new AchBuilder.EntryInput(name.getText(), id.getText(), routing.getText(),
                    account.getText(), type.getValue(), credit.isSelected(), prenote.isSelected(), value,
                    pt == null ? null : pt.getValue(), addenda.getText()));
        });
    }

    // ---- addenda ----------------------------------------------------------------------------

    static Optional<Result<String>> addenda(Window owner) {
        Form form = new Form("Add addenda", "Payment-related information (addenda type 05) shown to the receiver.");
        form.rawPaste("Paste any addenda record(s) (7), e.g. a return (99), NOC (98) or IAT addenda.");
        TextField text = form.text("Remittance text", "", 80, "Up to 80 characters, e.g. INV 10045 PO 2231");
        return form.showWithRaw(owner, () -> {
            if (text.getText().isBlank()) {
                throw new AchException("Enter the remittance text, or paste raw addenda line(s).");
            }
            return text.getText().trim();
        });
    }

    // ---- whole file from pasted text ---------------------------------------------------------

    static Optional<String> pastedFile(Window owner) {
        Form form = new Form("New from pasted text", "Paste the full contents of an ACH file (header 1 through file control 9).");
        TextArea area = new TextArea();
        area.getStyleClass().add("mono");
        area.setPrefRowCount(16);
        area.setPrefColumnCount(96);
        form.row("File contents", area);
        return form.show(owner, () -> {
            if (area.getText().isBlank()) {
                throw new AchException("Paste the file contents first.");
            }
            return area.getText();
        });
    }

    // ---- small form builder ----------------------------------------------------------------

    /** A two-column label/control grid in a dialog whose OK button validates before closing. */
    private static final class Form {
        private final Dialog<Object> dialog = new Dialog<>();
        private final GridPane grid = new GridPane();
        private final Label error = new Label();
        private TextArea raw;
        private int row;

        Form(String title, String intro) {
            dialog.setTitle(title);
            dialog.setHeaderText(intro);
            grid.setHgap(12);
            grid.setVgap(8);
            grid.getStyleClass().add("form");
            error.getStyleClass().add("form-error");
            error.setWrapText(true);
            error.setMaxWidth(560);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dialog.getDialogPane().setContent(new VBox(10, grid, error));
            dialog.getDialogPane().setMinWidth(640);
            dialog.getDialogPane().getStylesheets().add(Dialogs.class.getResource("ach.css").toExternalForm());
        }

        /** Adds a collapsible "paste raw line(s)" box; when filled it is used instead of the form. */
        void rawPaste(String hint) {
            raw = new TextArea();
            raw.getStyleClass().add("mono");
            raw.setPrefRowCount(3);
            raw.setPrefColumnCount(70);
            raw.setPromptText("Paste raw 94-character record(s) here…");
            Label info = small("When this box has text, the form fields below are ignored.\n" + hint);
            info.setWrapText(true);
            info.setMaxWidth(560);
            TitledPane pane = new TitledPane("Or paste raw line(s) instead of filling in the form", new VBox(4, raw, info));
            pane.setExpanded(false);
            pane.setAnimated(false);
            grid.add(pane, 0, row++, 2, 1);
        }

        TextField text(String label, String value, int max, String hint) {
            TextField field = new TextField(value == null ? "" : value);
            field.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().length() <= max ? c : null));
            field.setPrefColumnCount(Math.min(max + 2, 34));
            row(label, hint == null ? field : new VBox(2, field, small(hint)));
            return field;
        }

        <T> ComboBox<T> combo(String label, java.util.List<T> items, T value) {
            ComboBox<T> combo = new ComboBox<>();
            combo.getItems().setAll(items);
            combo.setValue(value);
            row(label, combo);
            return combo;
        }

        DatePicker date(String label, LocalDate value, String hint) {
            DatePicker picker = new DatePicker(value);
            row(label, hint == null ? picker : new VBox(2, picker, small(hint)));
            return picker;
        }

        CheckBox check(String label, boolean value) {
            CheckBox box = new CheckBox(label);
            box.setSelected(value);
            grid.add(box, 1, row++);
            return box;
        }

        Label hint(String text) {
            Label l = small(text);
            l.setWrapText(true);
            l.setMaxWidth(440);
            grid.add(l, 1, row++);
            return l;
        }

        void section(String title) {
            Label l = new Label(title);
            l.getStyleClass().add("form-section");
            grid.add(l, 0, row++, 2, 1);
        }

        void row(String label, Node control) {
            Label l = new Label(label);
            l.getStyleClass().add("form-label");
            grid.addRow(row++, l, control);
        }

        /** Shows live check-digit feedback under a routing number field. */
        void routingCheck(TextField field) {
            Label status = small("");
            Runnable check = () -> {
                String digits = field.getText().replaceAll("\\D", "");
                status.getStyleClass().removeAll("credit", "debit");
                if (digits.length() < 9) {
                    status.setText(digits.isEmpty() ? "" : digits.length() + " of 9 digits");
                } else if (AchFormat.isValidRouting(digits)) {
                    status.setText("✔ valid routing number");
                    status.getStyleClass().add("credit");
                } else {
                    status.setText("✖ check digit should be " + AchFormat.routingCheckDigit(digits));
                    status.getStyleClass().add("debit");
                }
            };
            field.textProperty().addListener(o -> check.run());
            check.run();
            grid.add(status, 1, row++);
        }

        /** Like {@link #show} but returns pasted raw lines when the raw box was used. */
        <T> Optional<Result<T>> showWithRaw(Window owner, Supplier<T> build) {
            return show(owner, () -> {
                if (raw != null && !raw.getText().isBlank()) {
                    com.fx.ach.core.AchService.pastedLines(raw.getText()); // validate early, inline
                    return new Result<T>(null, raw.getText());
                }
                return new Result<>(build.get(), null);
            });
        }

        @SuppressWarnings("unchecked")
        <T> Optional<T> show(Window owner, Supplier<T> build) {
            dialog.initOwner(owner);
            Object[] result = new Object[1];
            Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
            ok.addEventFilter(ActionEvent.ACTION, e -> {
                try {
                    result[0] = build.get();
                } catch (AchException | IllegalArgumentException ex) {
                    error.setText("✖ " + ex.getMessage());
                    e.consume();
                }
            });
            dialog.setResultConverter(b -> b == ButtonType.OK ? result[0] : null);
            return dialog.showAndWait().map(r -> (T) r);
        }

        private static Label small(String text) {
            Label l = new Label(text);
            l.getStyleClass().add("muted");
            return l;
        }
    }
}
