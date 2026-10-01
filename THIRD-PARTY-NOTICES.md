# Third-party notices

ACH Studio is licensed under the [Apache License 2.0](LICENSE).
The builds produced by `build.sh` (`ach-studio.jar`, the app image and the
installers) also contain the following third-party software, unmodified.

| Component | Version | License | Source |
|-----------|---------|---------|--------|
| jACH (`com.afrunt:jach`) | 0.3.4.2 | Apache License 2.0 | https://github.com/afrunt/jach |
| bean-metadata (`com.afrunt:bean-metadata`) | 0.4 | Apache License 2.0 | https://github.com/afrunt/bean-metadata |
| OpenJFX / JavaFX (`org.openjfx:javafx-base`, `-graphics`, `-controls`, `-fxml`) | 21.0.6 | GNU GPL v2 with the Classpath Exception | https://github.com/openjdk/jfx21u (tag `21.0.6+3`) |

The app image and installers additionally contain a Java runtime image created
with `jlink` from the JDK used to build them (OpenJDK, GNU GPL v2 with the
Classpath Exception). Its license files are included in the runtime's
`legal/` directory.

## jACH and bean-metadata

Copyright Andrii Frunt. Licensed under the Apache License, Version 2.0; the
full text is identical to this project's [LICENSE](LICENSE) file. Neither
project ships a NOTICE file.

## OpenJFX

OpenJFX is licensed under the GNU General Public License, version 2, with the
"Classpath" Exception, which permits linking it with independent modules such
as ACH Studio under terms of your choice. The full license text and the
accompanying information are in:

- [licenses/OpenJFX-LICENSE.txt](licenses/OpenJFX-LICENSE.txt)
- [licenses/OpenJFX-ADDITIONAL_LICENSE_INFO.txt](licenses/OpenJFX-ADDITIONAL_LICENSE_INFO.txt)
- [licenses/OpenJFX-ASSEMBLY_EXCEPTION.txt](licenses/OpenJFX-ASSEMBLY_EXCEPTION.txt)

The complete corresponding source code for the bundled OpenJFX binaries is
available at https://github.com/openjdk/jfx21u/tree/21.0.6%2B3 (the 21.0.6
release build) and from Maven Central as the `-sources` artifacts of
`org.openjfx:javafx-*:21.0.6`.

## Build-time only (not distributed)

JUnit 5 (Eclipse Public License 2.0) is used only to run the tests and is not
included in any build output.
