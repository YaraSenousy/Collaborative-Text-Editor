package apt.textclient;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.*;

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
    @FXML
    private Button undoButton;
    @FXML
    private Button redoButton;

    private WebSocketController wsController;
    private String username;
    private String writerCode;
    private String readerCode;
    private boolean accessPermission;
    private boolean isUpdatingTextArea = false;
    private Stack<String> undoStack = new Stack<>();
    private Stack<String> redoStack = new Stack<>();


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

        textArea.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.Z) {
                if (event.isShiftDown()) {
                    // lw shift is pressed yeb2a redo
                    redo();
                } else {
                    undo(); //lw la yeb2a undo
                }
                event.consume();
            }
        });
    }

    private void setupTextAreaListener() {
        textArea.textProperty().addListener((obs, oldValue, newValue) -> {
            if (isUpdatingTextArea) {
                return;
            }

            String username = wsController.getUsername();
            CRDTTree tree = wsController.getDocumentTree();
            int changeIndex = findFirstChangeIndex(oldValue, newValue);

            if (changeIndex >= 0) {
                long clock = wsController.getClock();


                if (newValue.length() > oldValue.length()) {
                    String parentId;

                    // Determine the parentId based on the change position
                    if (changeIndex == 0) {
                        parentId = tree.getRoot().getId(); // Insert at beginning
                    } else {
                        parentId = findNodeIdAtPosition(tree, changeIndex - 1);

                    }
                    // Insertion
                    int insertCount = newValue.length() - oldValue.length();
                    for (int i = 0; i < insertCount; i++) {
                        int newIndex = changeIndex + i;
                        char newChar = newValue.charAt(newIndex);
                        clock = wsController.getClock();
                        Node newNode = new Node(username, clock, parentId, newChar, 0);
                        wsController.sendChange(newNode);
                        parentId = newNode.getId();
                    }
                } else if (newValue.length() < oldValue.length()) {
                    // Deletion
                    String nodeIdToDelete = findNodeIdAtPosition(tree, changeIndex );;
                    Node deleteNode = new Node(username, clock, tree.getRoot().getId(), ' ', 1);
                    deleteNode.setId(nodeIdToDelete);
                    wsController.sendChange(deleteNode);
                }
                saveState();
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
            isUpdatingTextArea = true;
            int currentCaretPosition = textArea.getCaretPosition();
            List<Character> chars = wsController.getDocumentTree().traverse();
            StringBuilder content = new StringBuilder();
            chars.forEach(content::append);
            textArea.setText(content.toString());
            textArea.positionCaret(Math.min(currentCaretPosition, textArea.getText().length()));
            isUpdatingTextArea = false;
            saveState();
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
    private void saveState() {
        String currentContent = textArea.getText();
        if (undoStack.isEmpty() || !undoStack.peek().equals(currentContent)) {
            undoStack.push(currentContent);
            redoStack.clear(); // bnclear el redo stack on changes
        }
    }
    @FXML
    private void redo() {
        if (!redoStack.isEmpty() && accessPermission) {
            String redoContent = redoStack.pop();
            undoStack.push(redoContent);
            applyContent(redoContent);
        }
    }
    @FXML
    private void undo() {
        if (undoStack.size() > 1 && accessPermission) {
            String currentContent = undoStack.pop();
            redoStack.push(currentContent);
            String previousContent = undoStack.peek();
            applyContent(previousContent);
        }
    }
    private void applyContent(String content) {
        if (!accessPermission) return;

        String currentContent = textArea.getText();
        int changeIndex = findFirstChangeIndex(currentContent, content);
        CRDTTree tree = wsController.getDocumentTree();
        long clock = wsController.getClock();

        isUpdatingTextArea = true;
        // Handle deletions
        if (content.length() < currentContent.length()) {
            int deleteCount = currentContent.length() - content.length();
            for (int i = 0; i < deleteCount; i++) {
                String nodeIdToDelete = findNodeIdAtPosition(tree, changeIndex);
                if (nodeIdToDelete != null) {
                    Node deleteNode = new Node(username, clock, tree.getRoot().getId(), ' ', 1);
                    deleteNode.setId(nodeIdToDelete);
                    wsController.sendChange(deleteNode);
                    clock = wsController.getClock();
                }
            }
        }
        // Handle insertions
        else if (content.length() > currentContent.length()) {
            String parentId = changeIndex == 0 ? tree.getRoot().getId() : findNodeIdAtPosition(tree, changeIndex - 1);
            int insertCount = content.length() - currentContent.length();
            for (int i = 0; i < insertCount; i++) {
                int newIndex = changeIndex + i;
                char newChar = content.charAt(newIndex);
                Node newNode = new Node(username, clock, parentId, newChar, 0);
                wsController.sendChange(newNode);
                parentId = newNode.getId();
                clock = wsController.getClock();
            }
        }
        textArea.setText(content);
        isUpdatingTextArea = false;
    }


}