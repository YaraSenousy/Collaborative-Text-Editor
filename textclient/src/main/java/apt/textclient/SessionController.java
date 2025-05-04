package apt.textclient;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.application.Application;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.event.EventHandler;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.io.File;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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

    private long lastCursorUpdate = 0;
    private static final long CURSOR_UPDATE_INTERVAL = 100;

    private Stack<Node> undoStack = new Stack<>();
    private Stack<Node> redoStack = new Stack<>();
    private boolean isProcessingChanges = false;
    private int expectedCaretPosition = 0;


    public void initData(WebSocketController wsController, String username, String writerCode, String readerCode, boolean accessPermission) {
        this.wsController = wsController;
        this.username = username;
        this.writerCode = writerCode;
        this.readerCode = readerCode;
        this.accessPermission = accessPermission;
        System.out.println("access: "+accessPermission);
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
        if(accessPermission){
            textArea.setEditable(true);
        } else {textArea.setEditable(false);}
        //wsController.sendUserChange(new User(username, 1));
        listConnectedUsers();

        textArea.setEditable(accessPermission);
        undoButton.setDisable(!accessPermission);
        redoButton.setDisable(!accessPermission);
        exportButton.setDisable(!accessPermission);

        setButtonIcons();
        updateTextArea();
        setupTextAreaListener();
        setupCursorListener();
        wsController.setOnDocumentChange(this::updateTextArea);
//        textArea.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
//            if (event.isControlDown() && event.getCode() == KeyCode.Z) {
//                if (event.isShiftDown()) {
//                    redo();
//                } else {
//                    undo();
//                }
//                event.consume();
//            }
//        });

        wsController.setOnUsersChange(this::listConnectedUsers);
        Platform.runLater(() -> {
            Window window = textArea.getScene().getWindow();
            window.setOnCloseRequest(event -> {
                handleWindowClosing();
            });
        });
    }

    private void handleWindowClosing() {
        wsController.sendDisconnected();
    }

    private void setupCursorListener() {
        if (textArea != null && wsController != null) {
            // Update cursor position on key press or mouse click
            textArea.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                if (event.isControlDown() && event.getCode() == KeyCode.Z) {
                    if (event.isShiftDown()) {
                        redo();
                    } else {
                        undo();
                    }
                    event.consume();
                }
                sendThrottledCursorUpdate();
            });
            textArea.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                sendThrottledCursorUpdate();
            });
        } else {
            System.err.println("Error: textArea or wsController is null, cannot set up cursor listener.");
        }
    }

    private void sendThrottledCursorUpdate() {
        long now = System.currentTimeMillis();
        if (now - lastCursorUpdate >= CURSOR_UPDATE_INTERVAL) {
            int cursorPosition = textArea.getCaretPosition();
            //int caretPos = textArea.getCaretPosition();
            //int cursorPosition = textArea.getText(0, caretPos).replaceAll("[^\n]", "").length() + 1;
            User change=new User(username, cursorPosition,true);
            //change.setColor(wsController.getConnectedUsers().get(username).getColor());
            //change.setCursor(wsController.getConnectedUsers().get(username).getCursor());
            wsController.sendUserChange(change);
            lastCursorUpdate = now;
        }

    }
    private void setButtonIcons() {
        // Load images from resources (adjust paths based on your project structure)
        Image copyImage = new Image(getClass().getResourceAsStream("/icons/copy.png"));
        Image undoImage = new Image(getClass().getResourceAsStream("/icons/undo.png"));
        Image redoImage = new Image(getClass().getResourceAsStream("/icons/redo.png"));
        Image exportImage = new Image(getClass().getResourceAsStream("/icons/export.png"));

        // Create ImageView instances and set them as button graphics
        ImageView copyIcon = new ImageView(copyImage);
        copyIcon.setFitWidth(16); // Adjust size as needed
        copyIcon.setFitHeight(16);
        copyWriterCodeButton.setGraphic(copyIcon);

        ImageView copyReaderIcon = new ImageView(copyImage); // Reuse copy icon for reader
        copyReaderIcon.setFitWidth(16);
        copyReaderIcon.setFitHeight(16);
        copyReaderCodeButton.setGraphic(copyReaderIcon);

        ImageView undoIcon = new ImageView(undoImage);
        undoIcon.setFitWidth(16);
        undoIcon.setFitHeight(16);
        undoButton.setGraphic(undoIcon);

        ImageView redoIcon = new ImageView(redoImage);
        redoIcon.setFitWidth(16);
        redoIcon.setFitHeight(16);
        redoButton.setGraphic(redoIcon);

        ImageView exportIcon = new ImageView(exportImage);
        exportIcon.setFitWidth(16);
        exportIcon.setFitHeight(16);
        exportButton.setGraphic(exportIcon);
    }
    private void setupTextAreaListener() {
        textArea.textProperty().addListener((obs, oldValue, newValue) -> {
            if (isUpdatingTextArea || isProcessingChanges) {
                return;
            }
            isProcessingChanges = true;
            try {
                String username = wsController.getUsername();
                CRDTTree tree = wsController.getDocumentTree();
                int changeIndex = findFirstChangeIndex(oldValue, newValue);

                if (changeIndex >= 0) {
                    long baseClock = wsController.getClock();

                    if (newValue.length() > oldValue.length()) {
                        String parentId;

                        if (changeIndex == 0) {
                            parentId = tree.getRoot().getId();
                        } else {
                            parentId = findNodeIdAtPosition(tree, changeIndex - 1);

                        }

                        int insertCount = newValue.length() - oldValue.length();
                        for (int i = 0; i < insertCount; i++) {
                            int newIndex = changeIndex + i;
                            char newChar = newValue.charAt(newIndex);
                            long clock = wsController.getClock();
                            Node newNode = new Node(username, clock, parentId, newChar, 0);
                            wsController.sendChange(newNode);
                            tree.insert(newNode);
                            undoStack.push(newNode);
                            parentId = newNode.getId();
                        }
                        expectedCaretPosition = changeIndex + insertCount;
                    } else if (newValue.length() < oldValue.length()) {
                        int deleteCount = oldValue.length() - newValue.length();
                        List<Node> nodesToDelete = new ArrayList<>();
                        for (int i = 0; i < deleteCount; i++) {
                            int positionToDelete = changeIndex + i;
                            String nodeIdToDelete = findNodeIdAtPosition(tree, positionToDelete);
                            if (nodeIdToDelete != null) {
                                Node originalNode = tree.getNodeMap().get(nodeIdToDelete);
                                if (originalNode != null && !originalNode.isDeleted) {
                                    nodesToDelete.add(originalNode);
                                }
                            }
                        }
                        for (Node node : nodesToDelete) {
                            Node deleteNode = new Node(username, baseClock, tree.getRoot().getId(), node.getContent(), 1);
                            deleteNode.setId(node.getId());
                            wsController.sendChange(deleteNode);
                            tree.insert(deleteNode);
                            undoStack.push(deleteNode);
                        }
                        expectedCaretPosition = changeIndex;
                    }
                }
            } finally {
                isProcessingChanges = false;
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
            return minLength;
        }
        return -1;
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
                return;
            }
            isUpdatingTextArea = true;
            try {
                System.out.println("Updating screen");
                CRDTTree tree = wsController.getDocumentTree(); // Ensure we get the latest tree
                List<Character> chars = tree.traverse();
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
    private void exportDoc() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export document");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        File file = fileChooser.showSaveDialog(exportButton.getScene().getWindow());
        if (file != null) {
            wsController.getDocumentTree().export(file.getAbsolutePath());
        }
    }

    @FXML
    private void copyWriterCode() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(writerCode);
        clipboard.setContent(content);
    }

    @FXML
    private void copyReaderCode() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(readerCode);
        clipboard.setContent(content);
    }
    private void listConnectedUsers(){
        if (userListView == null || wsController == null) {
            System.err.println("Error: userListView or wsController is null, cannot update user list.");
            return;
        }

        userListView.getItems().clear();
        ConcurrentHashMap<String,User> connectedUsers=wsController.getConnectedUsers();
        // Get connected users from document
        if (connectedUsers == null) {
            System.err.println("Warning: getConnectedUsers returned null.");
            return;
        }
        //ArrayList<String> names=new ArrayList<>(connectedUsers.keySet());
        ArrayList<User> users=new ArrayList<>(connectedUsers.values());
        // Add users to ListView with line number placeholder
        for (int i = 0; i < connectedUsers.size(); i++) {
            String username=users.get(i).getUserName();
            int pos=users.get(i).getCursorPosition();
            if (username != null && !username.trim().isEmpty()) {
                userListView.getItems().add(username + " (Line: " + pos + ")");
            }
        }
}

    @FXML
    private void redo() {
        if (!redoStack.isEmpty()) {
            System.out.println("Redo");
            Node redoNode = redoStack.pop();
            CRDTTree tree = wsController.getDocumentTree();
            int nodePosition = findNodePosition(tree, redoNode.getId());
            redoNode.setOperation(redoNode.getOperation() ^ 1); // Toggle operation
            undoStack.push(redoNode);
            System.out.println("Redo node: " + redoNode.getContent() + ", operation: " + redoNode.getOperation() + ", clock: " + redoNode.getClock());
            if (redoNode.getOperation() == 0) { // Insert
                expectedCaretPosition = nodePosition + 1;
                tree.insert(redoNode);
            } else { // Delete
                expectedCaretPosition = nodePosition;
                tree.delete(redoNode.getId());
            }
            wsController.sendChange(redoNode);
            updateTextArea();
        }
    }

    @FXML
    private void undo() {
        if (!undoStack.isEmpty()) {
            System.out.println("Undo");
            Node undoNode = undoStack.pop();
            undoNode.setOperation(undoNode.getOperation() ^ 1); // Toggle operation
            redoStack.push(undoNode);
            System.out.println("Undo node: " + undoNode.getContent() + ", operation: " + undoNode.getOperation() + ", clock: " + undoNode.getClock());

            CRDTTree tree = wsController.getDocumentTree();
            int nodePosition = findNodePosition(tree, undoNode.getId());
            if (undoNode.getOperation() == 0) { // Insert
                expectedCaretPosition = nodePosition + 1;
                tree.insert(undoNode); // Apply locally
            } else { // Delete
                expectedCaretPosition = nodePosition;
                tree.delete(undoNode.getId()); // Apply locally
            }

            wsController.sendChange(undoNode);
            updateTextArea();
        }
    }
    private int findNodePosition(CRDTTree tree, String nodeId) {
        List<Node> nodesInOrder = new ArrayList<>();
        traverseForNodes(tree.getRoot(), nodesInOrder);
        for (int i = 0; i < nodesInOrder.size(); i++) {
            if (nodesInOrder.get(i).getId().equals(nodeId)) {
                return i;
            }
        }
        return 0; // Default to start if not found
    }
}