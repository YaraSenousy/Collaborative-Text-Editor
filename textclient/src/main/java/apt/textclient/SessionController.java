package apt.textclient;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.awt.SystemColor.text;


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
    @FXML
    private Pane cursorOverlay;

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
    private Map<String, Rectangle> cursor = new HashMap<>();
    private Timeline cursorBlinkTimeline;

    // Predefined array of 40 distinct color names
    private static final String[] COLOR_PALETTE = {
            "red", "blue", "green", "yellow", "purple", "orange", "pink", "brown", "gray", "cyan",
            "magenta", "lime", "teal", "indigo", "violet", "maroon", "navy", "olive", "silver", "gold",
            "coral", "turquoise", "salmon", "plum", "khaki", "aquamarine", "crimson", "darkgreen", "darkblue", "darkcyan",
            "darkmagenta", "darkorange", "darkred", "darkviolet", "deeppink", "lightblue", "lightgreen", "lightpink", "lightsalmon", "mediumpurple"
    };

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
        textArea.setEditable(accessPermission);
        textArea.setFont(new Font("Consolas", 14));
        System.out.println("TextArea font: " + textArea.getFont().getFamily() + ", size: " + textArea.getFont().getSize()); //figuring out cursor
        //System.out.println("CharWidth: " + getCharWidth() + ", LineHeight: " + getLineHeight());
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

        cursorBlinkTimeline = new Timeline(
                new KeyFrame(Duration.millis(500), event -> {
                    cursor.values().forEach(rect -> rect.setVisible(!rect.isVisible()));
                })
        );
        cursorBlinkTimeline.setCycleCount(Timeline.INDEFINITE);
        cursorBlinkTimeline.play();

        Timeline cursorRefreshTimeline = new Timeline(
                new KeyFrame(Duration.millis(50), event -> listConnectedUsers())
        );
        cursorRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        cursorRefreshTimeline.play();
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
            System.out.println("Sending cursor position for " + username + ": " + cursorPosition + ", text: " + textArea.getText());
            //int caretPos = textArea.getCaretPosition();
            //int cursorPosition = textArea.getText(0, caretPos).replaceAll("[^\n]", "").length() + 1;
            User change=new User(username, cursorPosition,true);
            User existingUser = wsController.getConnectedUsers().get(username);
            if(existingUser!=null && existingUser.getColor()!=null){
             change.setColor(existingUser.color);
            }else{
                String assignedColor = assignColorFromPalette(username);
                change.setColor(assignedColor);
                if (existingUser != null) {
                    existingUser.setColor(assignedColor);
                }
            }
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
                    sendThrottledCursorUpdate();
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
    private void listConnectedUsers() {
        // Null checks
        if (userListView == null || wsController == null || textArea == null || cursorOverlay == null) {
            System.err.println("Error: One or more components (userListView, wsController, textArea, cursorOverlay) is null.");
            return;
        }

        // Clear existing UI elements
        userListView.getItems().clear();
        cursorOverlay.getChildren().clear();

        // Get connected users
        ConcurrentHashMap<String, User> connectedUsers = wsController.getConnectedUsers();
        if (connectedUsers == null) {
            System.err.println("Warning: getConnectedUsers returned null.");
            return;
        }

        // Calculate text metrics
        double charWidth = getCharWidth();
        double lineHeight = getLineHeight();
        double textAreaWidth = textArea.getWidth() - textArea.getPadding().getLeft() - textArea.getPadding().getRight();
        int charsPerLine = (int) (textAreaWidth / charWidth);

        // Debug output for metrics
        System.out.println("Text metrics - Char width: " + charWidth +
                ", Line height: " + lineHeight +
                ", Chars per line: " + charsPerLine +
                ", Text length: " + textArea.getText().length());

        // Process each user
        ArrayList<User> users = new ArrayList<>(connectedUsers.values());
        for (User user : users) {
            String username = user.getUserName();
            int pos = Math.min(user.getCursorPosition(), textArea.getText().length());

            System.out.println("Processing user: " + username + ", pos: " + pos);

            // Add to user list
            if (username != null && !username.trim().isEmpty()) {
                userListView.getItems().add(username);
            }

            // Skip current user's cursor
            if (username.equals(this.username)) continue;

            // Get or create cursor rectangle
            Rectangle rect = cursor.computeIfAbsent(username, k -> {
                Rectangle r = new Rectangle(charWidth * 0.2, lineHeight);
                r.setVisible(true);
                return r;
            });

            // Set cursor color
            String color = user.getColor();
            if (color == null || color.trim().isEmpty()) {
                System.out.println("No color for " + username + ", assigning from palette.");
                color = assignColorFromPalette(username);
                user.setColor(color); // Update the User object
                // Notify other clients of the updated user color
                User updatedUser = new User(username, pos, true);
                updatedUser.setColor(color);
                wsController.sendUserChange(updatedUser);
            }
            try {
                if (color == null || color.trim().isEmpty()) {
                    rect.setFill(Color.web(color));
                    System.out.println("Set cursor color for " + username + " to " + color);
                } else {
                    rect.setFill(Color.web(color));
                }
            } catch (Exception e) {
                System.err.println("Invalid color for " + username + ": " + color + ", using default.");
                rect.setFill(Color.BLACK);
            }

            // Calculate cursor position
            String text = textArea.getText();
            int remainingPos = pos;
            int row = 0;
            int col = 0;
            int currentLineStart = 0;

            while (remainingPos > 0 && currentLineStart < text.length()) {
                int nextNewline = text.indexOf('\n', currentLineStart);
                if (nextNewline == -1) nextNewline = text.length();

                int segmentLength = Math.min(nextNewline - currentLineStart, remainingPos);
                String lineSegment = text.substring(currentLineStart, currentLineStart + segmentLength);

                int charsProcessed = 0;
                while (charsProcessed < segmentLength) {
                    int remainingInSegment = segmentLength - charsProcessed;
                    int charsThisLine = Math.min(remainingInSegment, charsPerLine - col);

                    if (charsThisLine <= 0) { // Wrap due to charsPerLine
                        row++;
                        int wrapPoint = currentLineStart + charsProcessed;
                        String wrappedText = text.substring(wrapPoint, Math.min(wrapPoint + remainingPos, text.length()));
                        // Count characters until space or end of segment
                        int wordLength = 0;
                        for (int i = 0; i < wrappedText.length() && i < remainingPos; i++) {
                            if (wrappedText.charAt(i) == ' ') break;
                            wordLength++;
                        }
                        col = wordLength; // Set col to the length of the first word
                        System.out.println("Debug: Wrap at " + wrapPoint + ", wrappedText='" + wrappedText + "', col set to " + col);
                        charsProcessed += col; // Move forward by the word length
                    } else {
                        charsProcessed += charsThisLine;
                        col += charsThisLine;
                        if (col >= charsPerLine) {
                            row++;
                            int wrapPoint = currentLineStart + charsProcessed;
                            String wrappedText = text.substring(wrapPoint, Math.min(wrapPoint + remainingPos, text.length()));
                            int wordLength = 0;
                            for (int i = 0; i < wrappedText.length() && i < remainingPos; i++) {
                                if (wrappedText.charAt(i) == ' ') break;
                                wordLength++;
                            }
                            col = wordLength; // Set col to the length of the wrapped word
                        }
                    }
                }

                remainingPos -= segmentLength;

                // Handle explicit newline
                if (nextNewline < text.length() && remainingPos > 0) {
                    row++;
                    col = 0;
                    remainingPos--;
                    currentLineStart = nextNewline + 1;
                } else {
                    currentLineStart += segmentLength;
                }
            }

            // Calculate final pixel position
            double x = col * charWidth + textArea.getPadding().getLeft() + 10.0 - textArea.getScrollLeft();
            double y = row * lineHeight + textArea.getPadding().getTop() + 5.0 - textArea.getScrollTop();

            // Boundary checks
            x = Math.max(0, Math.min(x, textAreaWidth - charWidth));
            y = Math.max(0, y);

            System.out.println("Final position - User: " + username +
                    ", pos: " + pos + ", row: " + row +
                    ", col: " + col + ", x: " + x + ", y: " + y);

            // Set cursor position
            rect.setX(x);
            rect.setY(y);
            cursorOverlay.getChildren().add(rect);
        }

        // Clean up disconnected users
        cursor.keySet().retainAll(connectedUsers.keySet());
    }

    private Position calculateCursorPosition(int pos, String text, int charsPerLine, double charWidth, double lineHeight) {
        int visualRow = 0;
        int visualCol = 0;

        for (int i = 0; i < pos && i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\n') {
                // New line
                visualRow++;
                visualCol = 0;
            } else {
                visualCol++;

                // Handle word wrapping
                if (visualCol > charsPerLine) {
                    // Find start of current word
                    int wordStart = i;
                    while (wordStart > 0 && !Character.isWhitespace(text.charAt(wordStart - 1))) {
                        wordStart--;
                    }

                    // Calculate how many characters we're wrapping
                    int wrappedChars = i - wordStart + 1;
                    visualRow++;
                    visualCol = wrappedChars; // Set to length of wrapped word
                }
            }
        }

        // Calculate pixel position
        double x = visualCol * charWidth + textArea.getPadding().getLeft() + 1;
        double y = visualRow * lineHeight + textArea.getPadding().getTop();

        return new Position(x, y);
    }

    // Helper classes remain the same
    private static class Position {
        final double x, y;
        Position(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private Color getUserColor(User user) {
        try {
            return user.getColor() != null ? Color.web(user.getColor()) : Color.BLACK;
        } catch (Exception e) {
            return Color.RED;
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

    private double getCharWidth() {
        javafx.scene.text.Text text = new javafx.scene.text.Text("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
        text.setFont(textArea.getFont());
        double width = text.getLayoutBounds().getWidth() / 52.0; // Average width
        System.out.println("CharWidth: " + width);
        return width ; // again trial and error idk what im doing atp
    }

    private double getLineHeight() {
        javafx.scene.text.Text text = new javafx.scene.text.Text("M\nM");
        text.setFont(textArea.getFont());
        double height = text.getLayoutBounds().getHeight() / 2.0;
        return height * 1.3; // trial and error
    }
//    private int calculateWrappedLines(String text, double maxWidth) {
//        if (text.isEmpty()) return 0;
//
//        javafx.scene.text.Text textNode = new javafx.scene.text.Text(text);
//        textNode.setFont(textArea.getFont());
//        double textWidth = textNode.getLayoutBounds().getWidth();
//
//        if (textWidth <= maxWidth) {
//            return 1;
//        }
//
//        // Simulate wrapping by breaking the text into chunks
//        int wrappedLines = 1;
//        double currentWidth = 0;
//        String[] words = text.split("(?<=\\s)|(?=\\s)"); // Split on whitespace boundaries
//        StringBuilder currentLine = new StringBuilder();
//
//        for (String word : words) {
//            javafx.scene.text.Text wordNode = new javafx.scene.text.Text(currentLine.toString() + word);
//            wordNode.setFont(textArea.getFont());
//            double wordWidth = wordNode.getLayoutBounds().getWidth();
//
//            if (currentWidth + wordWidth > maxWidth) {
//                wrappedLines++;
//                currentWidth = wordWidth;
//                currentLine = new StringBuilder(word);
//            } else {
//                currentWidth += wordWidth;
//                currentLine.append(word);
//            }
//        }
//
//        return wrappedLines;
//    }
private String assignColorFromPalette(String username) {
    if (username == null || username.trim().isEmpty()) {
        return COLOR_PALETTE[0]; // Default to first color if username is null/empty
    }
    int hash = Math.abs(username.hashCode());
    int colorIndex = hash % COLOR_PALETTE.length;
    return COLOR_PALETTE[colorIndex];
}


}