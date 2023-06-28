module com.graphiti.app.graphitiappai {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires com.google.gson;
    requires com.fazecast.jSerialComm;
    requires java.desktop;

    opens com.graphiti.app.graphitiappai to javafx.fxml;
    exports com.graphiti.app.graphitiappai;
}