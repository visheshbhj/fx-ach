package com.fx.ach;

import com.fx.ach.core.AchBuilder;
import com.fx.ach.core.AchTemplates;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Remembers the last folder and the originator details typed into the new-file form.
 */
final class Prefs {

    private static final Preferences P = Preferences.userNodeForPackage(Prefs.class);

    private Prefs() {
    }

    static Optional<Path> lastDir() {
        String dir = P.get("lastDir", null);
        return dir == null ? Optional.empty() : Optional.of(Path.of(dir)).filter(Files::isDirectory);
    }

    static void setLastDir(Path dir) {
        if (dir != null) {
            P.put("lastDir", dir.toString());
        }
    }

    static AchTemplates.Origin origin() {
        AchTemplates.Origin d = AchTemplates.sampleOrigin();
        AchBuilder.FileSettings f = d.file();
        return new AchTemplates.Origin(
                new AchBuilder.FileSettings(
                        P.get("destRouting", f.destinationRouting()),
                        P.get("destName", f.destinationName()),
                        P.get("origin", f.origin()),
                        P.get("originName", f.originName()),
                        "A"),
                P.get("companyName", d.companyName()),
                P.get("companyId", d.companyId()),
                P.get("odfi", d.odfiRouting()),
                d.effectiveDate());
    }

    static void setOrigin(AchTemplates.Origin o) {
        P.put("destRouting", o.file().destinationRouting());
        P.put("destName", o.file().destinationName());
        P.put("origin", o.file().origin());
        P.put("originName", o.file().originName());
        P.put("companyName", o.companyName());
        P.put("companyId", o.companyId());
        P.put("odfi", o.odfiRouting());
    }
}
