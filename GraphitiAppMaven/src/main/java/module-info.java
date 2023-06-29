module com.graphitiapp.graphitiappmaven {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires com.google.gson;
    requires com.fazecast.jSerialComm;
    requires java.desktop;

    opens com.graphitiapp.graphitiappmaven to javafx.fxml;
    exports com.graphitiapp.graphitiappmaven;
}