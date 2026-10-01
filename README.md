# ACH Studio

A desktop app for reading, building and editing NACHA ACH files, with every
94-character record presented in plain English.

- **Read** any ACH file. Each field is colour-coded and labelled, and codes are
  explained (transaction codes, SEC codes, return reasons, NOCs, IAT details…).
- **Build** files from templates (payroll, vendor payments, collections,
  international/IAT) or from scratch, using forms or by pasting raw lines.
- **Edit** any field. Batch and file control totals, entry hashes and counts
  are recalculated for you.
- **Validate** control totals, hashes, routing check digits, trace numbers,
  addenda indicators and more, and jump straight to the record with the problem.

Built with JavaFX on top of the [jACH](https://github.com/afrunt/jach) library.

## Requirements

- **JDK 21 or newer** to build (the project compiles for Java 21).
- To run the jar: Java 21+. The app image and installers bundle their own Java
  runtime, so end users need nothing installed.
- `jpackage` (included in the JDK) for the app image and installers. A Windows
  `.msi` also needs the [WiX Toolset](https://wixtoolset.org/).

Maven is not required; the Maven wrapper (`./mvnw`) downloads it.

## Building

```bash
./build.sh                 # runnable jar        -> target/ach-studio.jar
./build.sh app             # self-contained app  -> dist/ach-studio/
./build.sh installer       # native installer    -> dist/ (.deb/.rpm on Linux, .dmg on macOS, .msi on Windows)
./build.sh --skip-tests    # combine with any of the above
./build.sh --help
```

On Windows, use `build.ps1` in PowerShell instead:

```powershell
.\build.ps1                # runnable jar        -> target\ach-studio.jar
.\build.ps1 app            # self-contained app  -> dist\ach-studio\ach-studio.exe
.\build.ps1 installer      # .msi installer      -> dist\ (needs the WiX Toolset)
.\build.ps1 -SkipTests     # combine with any of the above
.\build.ps1 -Help
```

If PowerShell refuses to run scripts, use
`powershell -ExecutionPolicy Bypass -File .\build.ps1 [args]`.

The jar includes JavaFX for the operating system it was built on, so build on
each OS you want to ship for.

Other useful commands:

```bash
./mvnw javafx:run          # run from source
./mvnw test                # run the unit tests
```

## Running

```bash
java -jar target/ach-studio.jar [file.ach ...]   # jar
dist/ach-studio/bin/ach-studio [file.ach ...]    # app image (Linux; .app on macOS, .exe on Windows)
```

Files passed on the command line open in their own tabs. When run from the jar,
JavaFX logs an "Unsupported JavaFX configuration" warning at startup; it is
harmless.

Example files to try are in [`samples/`](samples): `payroll.ach`, `vendor.ach`,
`consumer_debit.ach`, `returns.ach` and `iat.ach`.

## Using ACH Studio

### Layout

| Area | What it shows |
|------|---------------|
| **Tabs** (centre) | One tab per open file. `*` marks unsaved changes; hover a tab for the file path. |
| **Raw view** (default) | The file's lines with every field shaded in its own colour. Hover a field to see its name, columns and meaning; click it to jump to that field in the form. A ruler above shows column numbers in colour-coded blocks of ten. |
| **Present mode** (toolbar toggle) | The same file as collapsible forms (file → batches → entries → addenda), each titled with a plain-English summary such as *Credit $2,450.00 · JANE DOE · Checking 123456789 @ 011000015*. |
| **Record panel** (right) | The selected record as a vertical form: each field's columns, value and meaning, plus a raw-line box. |
| **Validation** (bottom) | Problems in the active file. Click one to jump to the record. The toolbar badge shows the overall status. |

### Opening and creating files

- **File ▸ Open…** (or drag files onto the window). You can select several
  files; each opens in a new tab. Opening a file that is already open switches
  to its tab.
- **File ▸ New from template**: Payroll (PPD), Vendor payments (CCD with
  remittance), Customer collections (WEB), International payment (IAT) or an
  empty file. A form asks for the sending and receiving bank details and
  remembers them for next time.
- **File ▸ New from Pasted Text…**: paste the full contents of an ACH file.
- **File ▸ Save as Template…**: saves the current file to
  `~/.ach-studio/templates`. It then appears under *New from template*; new
  files made from it get today's date and a new effective date, and amounts can
  optionally be cleared.

Files with stripped trailing spaces, Windows line endings, no line breaks or
9-filled padding lines are read without complaint. Saved files are padded with
filler lines to a multiple of ten records, as banks expect.

### Editing records

1. Select a record in the raw view or Present mode.
2. Change fields in the record panel. Amounts are typed in dollars (`1250.00`);
   dates as `YYMMDD` or `YYYY-MM-DD`; coded fields have drop-downs.
3. Press **Apply** or Enter.

If you move to another record, tab or file with unapplied changes, you're asked
to Apply, Discard or Cancel. Turn on **Edit mode** to apply changes
automatically instead; Edit mode also deletes without asking.

After edits to batches and entries, the batch and file control records are
recalculated automatically. Edits you make to control records themselves are
kept as typed until the next recalculation.

### Adding records

- **Right-click** any record (raw view or Present mode) ▸ **Insert before** /
  **Insert after** ▸ Entry, Addenda or Batch. Positions that would break the
  file structure are greyed out (for example, an entry between another entry
  and its addenda).
- **+ Batch / + Entry** on the toolbar add next to the selected record.
- The entry form checks the routing number's check digit as you type and works
  out the transaction code from account type, credit/debit and prenote.

Entry forms support PPD, CCD, WEB and TEL. For other entry types (including IAT),
paste the raw lines instead.

### Working with raw lines

- **Record panel ▸ Raw line**: edit the line and press **Replace this record**,
  or paste one or more lines and press **Insert before** / **Insert after**.
- Every add dialog has an **Or paste raw line(s)** section; when it has text,
  the form fields are ignored.

You can paste several lines at once, such as a whole batch, or an entry with its
addenda. CR/LF is stripped and short lines are padded. The whole file is
re-read after pasting, so a line that doesn't fit where you put it is rejected
and nothing changes. Pasted lines keep their own trace numbers; use
**Edit ▸ Renumber Trace Numbers** if that creates duplicates.

### Other tools

| Action | Where |
|--------|-------|
| Recalculate all control totals | **Fix Totals** button / **Edit ▸ Recalculate Totals & Controls** |
| Renumber trace numbers | **Edit ▸ Renumber Trace Numbers** |
| Printable HTML summary | **Report** button / **File ▸ Export Readable Report (HTML)…** |
| Undo (per tab, up to 100 steps) | **Edit ▸ Undo** |

When saving a file with validation errors, you can fix the totals first or save
it as is.

### Keyboard shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+O | Open |
| Ctrl+S / Ctrl+Shift+S | Save / Save As |
| Ctrl+W | Close tab |
| Ctrl+Z | Undo (first discards unapplied field changes, if any) |
| Ctrl+E | Add entry |
| Ctrl+R | Recalculate totals |
| Delete | Delete the selected record (in the raw view) |
| Enter | Apply changes in the record panel |

On macOS, use Cmd instead of Ctrl.

## Project layout

```
src/main/java/com/fx/ach/core/   ACH logic, no UI: reading/writing (via jACH), field
                                 meanings, control totals, validation, templates,
                                 insert positions, HTML report
src/main/java/com/fx/ach/        JavaFX UI: main window, record panel, present mode,
                                 dialogs
src/main/resources/com/fx/ach/   FXML layout and stylesheet
src/test/java/                   Unit tests for the core logic
samples/                         Example ACH files
build.sh, build.ps1              Build scripts (jar, app image, installers)
```

## Limitations

- jACH stops reading at the first malformed line. The app reports which line,
  but can't display the rest of that file.
- The default effective date is the next weekday; bank holidays are not
  considered.
- Each tab holds a single ACH file (one file header and one file control).

## License

ACH Studio is licensed under the [Apache License 2.0](LICENSE). See
[NOTICE](NOTICE) and [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for the
bundled third-party software (jACH, bean-metadata and OpenJFX) and their
licenses.
