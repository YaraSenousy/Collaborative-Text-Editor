package apt.textclient;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
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
import java.util.concurrent.atomic.AtomicInteger;


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
    private ListView<String> commentsListView;
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

    private Button addCommentButton;
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

        listConnectedUsers();

        textArea.setEditable(accessPermission);
        undoButton.setDisable(!accessPermission);
        redoButton.setDisable(!accessPermission);
        exportButton.setDisable(!accessPermission);
        addCommentButton.setDisable(!accessPermission);

        userListView.setCellFactory(listView -> new ListCell<String>() {
            @Override
            protected void updateItem(String username, boolean empty) {
                super.updateItem(username, empty);
                if (empty || username == null) {
                    setText(null);
                    setStyle(""); // Reset style
                } else {
                    setText(username);
                    String color = wsController.getConnectedUsers().get(username).getColor();
                    if (color != null) {
                        try {
                            // Apply the color to the text
                            setStyle("-fx-text-fill: " + color + ";");
                        } catch (Exception e) {
                            System.err.println("Failed to apply color " + color + " to username " + username);
                            setStyle("-fx-text-fill: black;"); // Fallback to black
                        }
                    } else {
                        setStyle("-fx-text-fill: black;"); // Default color if not assigned
                    }
                }
            }
        });

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
        wsController.setOnCommentChange(this::updateComments);
        updateComments();
        setupCommentListener();
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

            User change=new User(username, cursorPosition,true);
//
            wsController.sendUserChange(change);
            lastCursorUpdate = now;
        }

    }
    private void setButtonIcons() {

        Image copyImage = new Image(getClass().getResourceAsStream("/icons/copy.png"));
        Image undoImage = new Image(getClass().getResourceAsStream("/icons/undo.png"));
        Image redoImage = new Image(getClass().getResourceAsStream("/icons/redo.png"));
        Image exportImage = new Image(getClass().getResourceAsStream("/icons/export.png"));
        Image commentsImage = new Image(getClass().getResourceAsStream("/icons/comments.png"));


        ImageView copyIcon = new ImageView(copyImage);
        copyIcon.setFitWidth(16); // Adjust size as needed
        copyIcon.setFitHeight(16);
        copyWriterCodeButton.setGraphic(copyIcon);

        ImageView copyReaderIcon = new ImageView(copyImage);
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

        ImageView commentsIcon = new ImageView(commentsImage);
        commentsIcon.setFitWidth(16);
        commentsIcon.setFitHeight(16);
        addCommentButton.setGraphic(commentsIcon);
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
                CRDTTree tree = wsController.getDocumentTree();
                List<Character> chars = tree.traverse();
                StringBuilder content = new StringBuilder();
                chars.forEach(content::append);
                String newText = content.toString();
                textArea.setText(newText);
                textArea.positionCaret(Math.min(expectedCaretPosition, newText.length()));

                // Adjust comments based on the text change
                adjustComments();
                updateComments();
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



        // Process each user
        ArrayList<User> users = new ArrayList<>(connectedUsers.values());
        final int[] colorIndex = {0};
        for (User user : users) {
            String username = user.getUserName();
            if (username == null || username.trim().isEmpty()) continue;
        }
        int colorNumber = 0;
        for (User user : users) {
            String username = user.getUserName();
            int pos = Math.min(user.getCursorPosition(), textArea.getText().length());


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


            // Assign a random color from the palette
            String randomColor = COLOR_PALETTE[colorNumber];
            colorNumber++;
            try {
                rect.setFill(Color.web(randomColor));
            } catch (Exception e) {
                System.err.println("Invalid color for " + username + ": " + randomColor + ", using default.");
                rect.setFill(Color.BLACK);
            }
            user.setColor(randomColor);

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
            Node redoNode = redoStack.pop();
            CRDTTree tree = wsController.getDocumentTree();
            int nodePosition = findNodePosition(tree, redoNode.getId());
            redoNode.setOperation(redoNode.getOperation() ^ 1); // Toggle operation
            undoStack.push(redoNode);
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
            Node undoNode = undoStack.pop();
            undoNode.setOperation(undoNode.getOperation() ^ 1); // Toggle operation
            redoStack.push(undoNode);

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

    private void adjustComments() {
        CRDTTree tree = wsController.getDocumentTree();
        ConcurrentHashMap<String, Comment> comments = wsController.getComments();
        if (comments == null) {
            return;
        }

        List<String> commentsToRemove = new ArrayList<>();
        for (Map.Entry<String, Comment> entry : comments.entrySet()) {
            Comment comment = entry.getValue();
            Node startNode = tree.getNodeMap().get(comment.getStartNodeId());
            Node endNode = tree.getNodeMap().get(comment.getEndNodeId());

            // Remove comment if either start or end node is deleted or doesn't exist
            if (startNode == null || endNode == null || startNode.isDeleted || endNode.isDeleted) {
                commentsToRemove.add(entry.getKey());
                continue;
            }

            // Compute new positions by traversing the tree
            List<Node> nodesInOrder = new ArrayList<>();
            traverseForNodes(tree.getRoot(), nodesInOrder);
            int startPos = -1;
            int endPos = -1;
            for (int i = 0; i < nodesInOrder.size(); i++) {
                Node node = nodesInOrder.get(i);
                if (node.getId().equals(comment.getStartNodeId())) {
                    startPos = i;
                }
                if (node.getId().equals(comment.getEndNodeId())) {
                    endPos = i;
                    break;
                }
            }

            // If positions couldn't be determined, mark for removal
            if (startPos == -1 || endPos == -1) {
                commentsToRemove.add(entry.getKey());
            } else {
                comment.setStartPosition(startPos);
                comment.setEndPosition(endPos + 1); // End position is exclusive
            }
        }

        // Remove comments and notify others
        for (String commentId : commentsToRemove) {
            Comment comment = comments.get(commentId);
            if (comment != null) {
                Comment deleteComment = new Comment(comment.getUser(), comment.getText(), comment.getStartNodeId(), comment.getEndNodeId(), 1);
                deleteComment.setId(comment.getId());
                wsController.sendComment(deleteComment); // Notify others
                comments.remove(commentId); // Remove locally
            }
        }
    }
    private void setupCommentListener() {
        if (addCommentButton != null) {
            addCommentButton.setOnAction(event -> addComment());
        }

        if (commentsListView != null) {
            commentsListView.setOnMouseClicked(event -> {
                String selectedComment = commentsListView.getSelectionModel().getSelectedItem();
                if (selectedComment != null) {
                    // Fetch the current list of comments to ensure it's up-to-date
                    ConcurrentHashMap<String, Comment> comments = wsController.getComments();
                    if (comments != null) {
                        for (Comment comment : comments.values()) {
                            if (comment.toString().equals(selectedComment)) {
                                // Highlight the range in the TextArea
                                textArea.selectRange(comment.getStartPosition(), comment.getEndPosition());
                                break;
                            }
                        }
                    }
                }
            });
        }
    }

    @FXML
    private void addComment() {
        if (!accessPermission) {
            showAlert("Permission Denied", "You do not have permission to add comments.");
            return;
        }

        int startPosition = textArea.getSelection().getStart();
        int endPosition = textArea.getSelection().getEnd();
        if (startPosition == endPosition) {
            showAlert("No Selection", "Please select a range of text to comment on.");
            return;
        }

        // Find the start and end node IDs for the selected range
        CRDTTree tree = wsController.getDocumentTree();
        String startNodeId = findNodeIdAtPosition(tree, startPosition);
        String endNodeId = findNodeIdAtPosition(tree, endPosition - 1);
        if (startNodeId == null || endNodeId == null) {
            showAlert("Invalid Selection", "Cannot add comment: selected range is invalid.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Comment");
        dialog.setHeaderText("Enter your comment for range " + startPosition + "-" + endPosition);
        dialog.setContentText("Comment:");
        Optional<String> result = dialog.showAndWait();

        result.ifPresent(commentText -> {
            Comment comment = new Comment(username, commentText, startNodeId, endNodeId, 0);
            comment.setStartPosition(startPosition);
            comment.setEndPosition(endPosition);
            wsController.getComments().put(comment.getId(), comment);
            wsController.sendComment(comment);
            updateComments();
        });
    }

    private void updateComments() {
        if (commentsListView != null) {
            commentsListView.getItems().clear();
            ArrayList<Comment> comments=new ArrayList<>(wsController.getComments().values());
            comments.sort(Comparator.comparing(Comment::getStartPosition));
            for (Comment comment : comments) {
                commentsListView.getItems().add(comment.toString());
            }
        }
    }
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private double getCharWidth() {
        javafx.scene.text.Text text = new javafx.scene.text.Text("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
        text.setFont(textArea.getFont());
        double width = text.getLayoutBounds().getWidth() / 52.0; // Average width
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