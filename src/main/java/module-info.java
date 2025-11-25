module com.example.demo {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires java.sql;

    exports com.example.demo;
    opens com.example.demo to javafx.graphics, javafx.fxml;
}