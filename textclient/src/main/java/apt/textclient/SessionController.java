package apt.textclient;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
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
    @FXML
    private Button exportButton;
    @FXML
    private Button copyWriterCodeButton;
    @FXML
    private Button copyReaderCodeButton;
    @FXML
    private HBox writerCodeBox;
    @FXML
    private HBox readerCodeBox;

    private WebSocketController wsController;
    private String username;
    private String writerCode;
    private String readerCode;
    private boolean accessPermission;
    private boolean isUpdatingTextArea = false;
    private boolean isProcessingChanges = false;
    private int expectedCaretPosition = 0;

    public void initData(WebSocketController wsController, String username, String writerCode, String readerCode, boolean accessPermission) {
        this.wsController = wsController;
        this.username = username;
        this.writerCode = writerCode;
        this.readerCode = readerCode;
        this.accessPermission = accessPermission;
        if (!Objects.equals(writerCode, "")) {
            writerCodeBox.setVisible(true);
            readerCodeBox.setVisible(true);
            writerCodeLabel.setText("Writer Code");
            readerCodeLabel.setText("Reader Code");
        } else {
            writerCodeBox.setVisible(false);
            readerCodeBox.setVisible(false);
            writerCodeBox.setManaged(true);
        }

        userListView.getItems().add(username + " (Line: 1)");
        userListView.getItems().add("User2 (Line: 3)");
        userListView.getItems().add("User3 (Line: 5)");

        updateTextArea();
        setupTextAreaListener();
        wsController.setOnDocumentChange(this::updateTextArea);
    }

    private void setupTextAreaListener() {
        textArea.textProperty().addListener((obs, oldValue, newValue) -> {
            if (isUpdatingTextArea || isProcessingChanges) {
                System.out.println("Listener skipped: Updating=" + isUpdatingTextArea + ", Processing=" + isProcessingChanges);
                return;
            }

            System.out.println("Entering listener");
            isProcessingChanges = true;
            try {
                String username = wsController.getUsername();
                CRDTTree tree = wsController.getDocumentTree();
                int changeIndex = findFirstChangeIndex(oldValue, newValue);
                ArrayList<Node> nodesToSend = new ArrayList<>();
                if (changeIndex >= 0) {
                    long clock = wsController.getClock();

                    if (newValue.length() > oldValue.length()) {
                        String parentId;

                        if (changeIndex == 0) {
                            parentId = tree.getRoot().getId();
                        } else {
                            parentId = findNodeIdAtPosition(tree, changeIndex - 1);
                            if (parentId == null) {
                                parentId = tree.getRoot().getId();
                            }
                        }

                        int insertCount = newValue.length() - oldValue.length();

                        System.out.println("Inserting " + insertCount + " characters at index " + changeIndex);
                        for (int i = 0; i < insertCount; i++) {
                            int newIndex = changeIndex + i;
                            if (newIndex < newValue.length()) {
                                char newChar = newValue.charAt(newIndex);
                                clock = wsController.getClock() ;
                                Node newNode = new Node(username, clock, parentId, newChar, 0);
                                System.out.println("Created node: id=" + newNode.getId() + ", char='" + newChar + "', parentId=" + parentId);
                                nodesToSend.add(newNode);
                                parentId = newNode.getId();
                            }
                        }
                        System.out.println("Sending " + nodesToSend.size() + " nodes to server");
                        expectedCaretPosition = changeIndex + insertCount;

                    } else if (newValue.length() < oldValue.length()) {
                        String nodeIdToDelete = findNodeIdAtPosition(tree, changeIndex);
                        if (nodeIdToDelete != null) {
                            Node deleteNode = new Node(username, clock, tree.getRoot().getId(), ' ', 1);
                            deleteNode.setId(nodeIdToDelete);
                            System.out.println("Sending delete node for id=" + nodeIdToDelete);
                            nodesToSend.add(deleteNode);
                        }
                        expectedCaretPosition = changeIndex;
                    }
                    wsController.sendChange(nodesToSend);

                }
            } finally {
                isProcessingChanges = false;
                System.out.println("Exiting listener");
            }
        });
    }

    private int findFirstChangeIndex(String oldValue, String newValue) {
        int minLength = Math.min(oldValue.length(), newValue.length());
        for (int i = 0; i < minLength; i++) {
            if (newValue.charAt(i) != oldValue.charAt(i)) {
                return i;
            }
        }
        if (newValue.length() != oldValue.length()) {
            return minLength; // Return the position where length differs
        }
        return -1; // No change
    }

    private String findNodeIdAtPosition(CRDTTree tree, int position) {
        List<Node> nodesInOrder = new ArrayList<>();
        traverseForNodes(tree.getRoot(), nodesInOrder);

        if (position < 0 || position >= nodesInOrder.size()) {
            return null;
        }
        return nodesInOrder.get(position).getId();
    }

    private void traverseForNodes(Node node, List<Node> nodesInOrder) {
        if (node == null) return;
        // Use the TreeSet's natural order (reversed clock)
        for (Node child : node.children) {
            if (!child.isDeleted) {
                nodesInOrder.add(child);
            }
            traverseForNodes(child, nodesInOrder);
        }
    }

    private void updateTextArea() {
        Platform.runLater(() -> {
            if (isUpdatingTextArea) {
                System.out.println("Update text area skipped: Already updating");
                return;
            }
            isUpdatingTextArea = true;
            try {
                int currentCaretPosition = textArea.getCaretPosition();
                List<Character> chars = wsController.getDocumentTree().traverse();
                StringBuilder content = new StringBuilder();
                chars.forEach(content::append);
                textArea.setText(content.toString());
                textArea.positionCaret(Math.min(expectedCaretPosition, content.length()));
            } finally {
                isUpdatingTextArea = false;
            }
        });
    }
    @FXML
    private void exportDoc(){
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export document");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        File file = fileChooser.showSaveDialog(exportButton.getScene().getWindow());
        if(file!=null){
            wsController.getDocumentTree().export(file.getAbsolutePath());
        }
    }
    @FXML
    private void copyWriterCode(){
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();

        content.putString(writerCode);
        clipboard.setContent(content);
    }
    @FXML
    private void copyReaderCode(){
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();

        content.putString(readerCode);
        clipboard.setContent(content);
    }
}