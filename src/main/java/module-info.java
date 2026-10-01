module com.fx.ach {
    requires javafx.controls;
    requires javafx.fxml;
    requires jach;
    requires java.prefs;

    opens com.fx.ach to javafx.fxml;
    exports com.fx.ach;
    exports com.fx.ach.core;
    exports com.fx.ach.bai2;
}
