module apt.textclient {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires spring.messaging;
    requires spring.websocket;
    requires spring.core;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;

    opens apt.textclient to javafx.fxml;
    exports apt.textclient;
}