package apt.textclient;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;

import java.util.List;
import java.util.Objects;

public class SessionController {
    @FXML
    private TextArea textArea;
    @FXML
    private Label writerCodeLabel;
    @FXML
    private Label readerCodeLabel;
    @FXML
    private ListView<String> userListView;

    private WebSocketController wsController;
    private String username;
    private String writerCode;
    private String readerCode;
    private long clock;
    private boolean accessPermission;

    public void initData(WebSocketController wsController, String username, String writerCode, String readerCode, boolean accessPermission) {
        this.wsController = wsController;
        this.username = username;
        this.writerCode = writerCode;
        this.readerCode = readerCode;
        this.accessPermission = accessPermission;
        //this.clock = wsController.getClock();

            writerCodeLabel.setText("Writer Code: " + writerCode);
            readerCodeLabel.setText("Reader Code: " + readerCode);


        userListView.getItems().add(username + " (Line: 1)");
        // Placeholder for other users (to be updated via WebSocket)
        userListView.getItems().add("User2 (Line: 3)");
        userListView.getItems().add("User3 (Line: 5)");
        setupTextAreaListener();
        //wsController.setOnDocumentChange(this::updateTextArea);
        updateTextArea();
    }

    private void setupTextAreaListener() {
        textArea.textProperty().addListener((obs, oldValue, newValue) -> {
            String parentId = wsController.getDocumentTree().getRoot().getId(); // Root node as parent initially
            long clock = wsController.getClock(); // Assume you have a clock tracker for each user
            String username = wsController.getUsername(); // Assuming you have a way to get the current user's ID
            //String parentId = wsController.getDocumentTree().getRoot().getId();
            // Handle insertions
            for (int i = 0; i < newValue.length(); i++) {
                if (i >= oldValue.length() || newValue.charAt(i) != oldValue.charAt(i)) {
                    Node newNode = new Node(username, clock++, parentId, newValue.charAt(i), 0);
                    wsController.sendChange(newNode);
                }
            }
            // Handle deletions
            if (newValue.length() < oldValue.length()) {
                for (int i = newValue.length(); i < oldValue.length(); i++) {
                    Node deleteNode = new Node(username, clock++, parentId, '\0', 1);
                    wsController.sendChange(deleteNode);
                }
            }
            //wsController.setClock(clock);
        });
    }

    private void updateTextArea() {
        Platform.runLater(() -> {
            List<Character> chars = wsController.getDocumentTree().traverse();
            StringBuilder content = new StringBuilder();
            chars.forEach(content::append);
            textArea.setText(content.toString());
        });
    }
}