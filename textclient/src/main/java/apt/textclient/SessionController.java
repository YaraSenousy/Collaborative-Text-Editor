package apt.textclient;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.application.Application;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.event.EventHandler;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    private long lastCursorUpdate = 0;
    private static final long CURSOR_UPDATE_INTERVAL = 100;
    private Pane cursorLayer;
    private Map<String, Rectangle> userCursors = new ConcurrentHashMap<>();
    private static final Map<String, Color> userColors = new ConcurrentHashMap<>();

    public void initData(WebSocketController wsController, String username, String writerCode, String readerCode, boolean accessPermission) {
        this.wsController = wsController;
        this.username = username;
        this.writerCode = writerCode;
        this.readerCode = readerCode;
        this.accessPermission = accessPermission;
        if (!Objects.equals(writerCode, "")) {
            writerCodeBox.setVisible(true);
            readerCodeBox.setVisible(true);
        } else {
            writerCodeBox.setVisible(false);
            readerCodeBox.setVisible(false);
        }
        if(accessPermission){
            textArea.setEditable(true);
        } else {textArea.setEditable(false);}
        //wsController.sendUserChange(new User(username, 1));
        listConnectedUsers();

        cursorLayer = (Pane) textArea.getScene().lookup("#cursorLayer");

        updateTextArea();
        setupTextAreaListener();
        setupCursorListener();
        wsController.setOnDocumentChange(this::updateTextArea);
        wsController.setOnUsersChange(this::listConnectedUsers);
        Platform.runLater(() -> {
            Window window = textArea.getScene().getWindow();
            window.setOnCloseRequest(event -> {
                handleWindowClosing();
            });
        });

        if (userColors.isEmpty()) {  // Only generate colors once
            wsController.getConnectedUsers().keySet()
                    .forEach(user -> userColors.putIfAbsent(user, generateColorForUsername(user)));
        }
        generateColorForUsername(username);

        //userColors.clear();
    }

    private void handleWindowClosing() {
        wsController.sendDisconnected();
    }

    private void setupCursorListener() {
        if (textArea != null && wsController != null) {
            // Update cursor position on key press or mouse click
            textArea.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                sendThrottledCursorUpdate();
                updateAllCursors();
            });
            textArea.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                sendThrottledCursorUpdate();
                updateAllCursors();
            });
        } else {
            System.err.println("Error: textArea or wsController is null, cannot set up cursor listener.");
        }
    }

    private void sendThrottledCursorUpdate() {
        long now = System.currentTimeMillis();
        if (now - lastCursorUpdate >= CURSOR_UPDATE_INTERVAL) {
            int cursorPosition = textArea.getCaretPosition();
            wsController.sendUserChange(new User(username, cursorPosition,true));
            lastCursorUpdate = now;
        }
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
        });
    }

    private void updateUserCursor(String username, int position) {
        Platform.runLater(() -> {
            try {
                // Remove old cursor if exists
                Rectangle oldCursor = userCursors.get(username);
                if (oldCursor != null) {
                    cursorLayer.getChildren().remove(oldCursor);
                }

                // Validate position
                if (position < 0 || position > textArea.getText().length()) {
                    return;
                }

                // Get or create user color
                Color userColor = userColors.computeIfAbsent(username, this::generateColorForUsername);

                // Create new cursor
                Rectangle cursor = new Rectangle(2, textArea.getFont().getSize() + 4, userColor);
                cursor.setOpacity(0.7);

                // Position cursor
                String textUpToCursor = textArea.getText(0, position);
                String[] lines = textUpToCursor.split("\n", -1);
                int lineNumber = lines.length - 1;
                String currentLine = lines[lineNumber];

                // Calculate X position (approximate)
                double charWidth = textArea.getFont().getSize() * 0.6;
                double xPos = currentLine.length() * charWidth + textArea.getPadding().getLeft();

                // Calculate Y position
                double lineHeight = textArea.getFont().getSize() * 1.2;
                double yPos = lineNumber * lineHeight + textArea.getPadding().getTop();

                cursor.setLayoutX(xPos);
                cursor.setLayoutY(yPos);

                // Add blinking animation
                FadeTransition blink = new FadeTransition(Duration.millis(1000), cursor);
                blink.setFromValue(0.7);
                blink.setToValue(0.2);
                blink.setCycleCount(Animation.INDEFINITE);
                blink.setAutoReverse(true);
                blink.play();

                // Store cursor reference
                cursorLayer.getChildren().add(cursor);
                userCursors.put(username, cursor);

            } catch (Exception e) {
                System.err.println("Error updating cursor: " + e.getMessage());
            }
        });
    }

    private void updateAllCursors() {
        if (wsController != null && wsController.getConnectedUsers() != null) {
            wsController.getConnectedUsers().forEach((user, pos) -> {
                // Get existing cursor (if any)
                Rectangle existingCursor = userCursors.get(user);

                // Calculate expected position
                double expectedX = calculateCursorX(pos);

                // Update if:
                // 1. No cursor exists for this user, OR
                // 2. Cursor position changed significantly (>1px tolerance)
                if (existingCursor == null ||
                        Math.abs(existingCursor.getLayoutX() - expectedX) > 1.0) {
                    updateUserCursor(user, pos);
                }
            });
        }
    }

    private double calculateCursorX(int position) {
        if (textArea == null || position < 0) return 0;

        try {
            String text = textArea.getText();
            int adjustedPos = Math.min(position, text.length());
            String textToCursor = text.substring(0, adjustedPos);

            // Get the line and column
            int lastNewLine = textToCursor.lastIndexOf('\n');
            int column = (lastNewLine == -1)
                    ? adjustedPos
                    : adjustedPos - lastNewLine - 1;

            // Approximate character width (monospace assumption)
            double charWidth = textArea.getFont().getSize() * 0.6;
            return column * charWidth + textArea.getPadding().getLeft();
        } catch (Exception e) {
            System.err.println("Cursor position calculation error: " + e.getMessage());
            return 0;
        }
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
    private void listConnectedUsers(){
        if (userListView == null || wsController == null) {
            System.err.println("Error: userListView or wsController is null, cannot update user list.");
            return;
        }

        userListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String username, boolean empty) {
                super.updateItem(username, empty);

                if (empty || username == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    //String username = item.split(" ")[0]; // Extract just the name
                    //Color userColor = userColors.computeIfAbsent(username, SessionController.this::generateColorForUsername);
                    Text coloredText = new Text(username);
                    coloredText.setFill(userColors.get(username));
                    setGraphic(coloredText);
                }
            }
        });

        userListView.getItems().clear();
        ConcurrentHashMap<String,Integer> connectedUsers=wsController.getConnectedUsers();
        // Get connected users from document
        if (connectedUsers == null) {
            System.err.println("Warning: getConnectedUsers returned null.");
            return;
        }
        ArrayList<String> names=new ArrayList<>(connectedUsers.keySet());
        ArrayList<Integer> cursorpos=new ArrayList<>(connectedUsers.values());
        // Add users to ListView with line number placeholder
        for (int i = 0; i < connectedUsers.size(); i++) {
            String user=names.get(i);
            int pos=cursorpos.get(i);
            if (user != null && !user.trim().isEmpty()) {
                userListView.getItems().add(user + " (Line: " + pos + ")");
            }
        }
    }

    private Color generateColorForUsername(String username) {
        // This will now return the same color for same username across sessions
        return userColors.computeIfAbsent(username,
                un -> Color.hsb(Math.abs(un.hashCode() % 360), 0.7, 0.9));
    }

}

