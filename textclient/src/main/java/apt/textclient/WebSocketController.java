package apt.textclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.springframework.util.concurrent.ListenableFutureCallback;

@Getter
@Setter
public class WebSocketController {
    private String SERVER_URL="localhost";
    private String joinCode;
    private StompSession stompSession;
    private String username;
    private String docId;
    private CRDTTree documentTree = new CRDTTree();
    private ObjectMapper objectMapper = JacksonConfig.getObjectMapper();
    private Runnable onDocumentChange;
    private Runnable onUsersChange;
    private ConcurrentHashMap<String, User> connectedUsers;

    private boolean isConnected = false;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final List<Node> offlineOperations = Collections.synchronizedList(new ArrayList<>());
    private long disconnectTime = 0;
    private static final long RECONNECT_WINDOW_MS = 5 * 60 * 1000; // 5 minutes
    private static final long RECONNECT_INTERVAL_MS = 5000; // Retry every 5 seconds

    public void initializeData( String username,String docId,ConcurrentHashMap<String,User> connectedUsers,String joinCode,ConcurrentHashMap<String,Comment> comments) {

    private Runnable onCommentChange;
    private ConcurrentHashMap<String,Comment> comments;


        this.username = username;
        this.comments = comments;
        this.docId = docId;
        this.connectedUsers=connectedUsers;
        this.joinCode=joinCode;
        connectToWebSocket(username, docId);
        System.out.println("docId after connecttowebsock "+docId+" username: "+username);
    }
    private void handleReceivedNode(Node newNode) {
        System.out.println("Received node: content=" + newNode.getContent() + ", operation=" + newNode.getOperation() + ", id=" + newNode.getId() + ", parentId=" + newNode.getParentId());
        if (newNode.getOperation() == 0) {
            System.out.println("Attempting to insert node: " + newNode.getContent());
            this.documentTree.insert(newNode);
        } else {
            System.out.println("Attempting to delete node with id: " + newNode.getId());
            this.documentTree.delete(newNode.getId());
        }
        if (onDocumentChange != null) {
            Platform.runLater(onDocumentChange);
            System.out.println("Triggered onDocumentChange");
        }
    }
    private void handleReceivedChange(User newuser){
        if(newuser.isConnected) {
            connectedUsers.put(newuser.getUserName(), newuser);
        }else{
            connectedUsers.remove(newuser.getUserName());
        }
        if (onUsersChange != null) {
            Platform.runLater(onUsersChange);
        }
    }
    private void handleComment(Comment newComment){
        if(newComment.getOperation() == 0) {
           comments.put(newComment.getId(),newComment);
        }else{
            comments.remove(newComment.getId());
        }
        if (onCommentChange != null) {
            Platform.runLater(onCommentChange);
        }
    }

    public void sendDisconnected() {
        // Send disconnection message
        User disconnectMessage = new User(username, 0, false);
        sendUserChange(disconnectMessage);
    }
    private void connectToWebSocket(String username, String roomId) {
        try {
            List<Transport> transports = Collections.singletonList(
                    new WebSocketTransport(new StandardWebSocketClient())
            );
            SockJsClient sockJsClient = new SockJsClient(transports);
            WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);

            List<MessageConverter> converters = new ArrayList<>();
            converters.add(new MappingJackson2MessageConverter());
            stompClient.setMessageConverter(new CompositeMessageConverter(converters));

            String url = "ws://"+SERVER_URL+":8080/ws";
            StompSessionHandler sessionHandler = new MyStompSessionHandler(this,username,docId);

            // Add connection callback
            stompClient.connect(url, sessionHandler)
                    .addCallback(new ListenableFutureCallback<StompSession>() {
                        @Override
                        public void onSuccess(StompSession session) {
                            isConnected = true;
                            stompSession = session;
                            System.out.println("Successfully connected");
                            // Subscribe to the chat room
                            String topic = "/topic/document/" + docId;
                            stompSession.subscribe(topic, new StompFrameHandler() {

                                @Override
                                public Type getPayloadType(StompHeaders headers) {
                                    return Node.class; // Expected payload type
                                }
                                @Override
                                public void handleFrame(StompHeaders headers, Object payload) {
                                    try{
                                    Node change = (Node) payload;
                                    handleReceivedNode(change);}
                                    catch(Exception e){
                                        System.err.println(e.getMessage());
                                    }

                                }
                            });
                            // Subscribe to the changes
                            topic = "/topic/change/" + docId;
                            stompSession.subscribe(topic, new StompFrameHandler() {

                                @Override
                                public Type getPayloadType(StompHeaders headers) {
                                    return User.class; // Expected payload type
                                }
                                @Override
                                public void handleFrame(StompHeaders headers, Object payload) {
                                    try{
                                        User change = (User) payload;
                                        handleReceivedChange(change);}
                                    catch(Exception e){
                                        System.err.println(e.getMessage());
                                    }

                                }
                            });
                            // Subscribe to the comments
                            topic = "/topic/comment/" + docId;
                            stompSession.subscribe(topic, new StompFrameHandler() {

                                @Override
                                public Type getPayloadType(StompHeaders headers) {
                                    return Comment.class; // Expected payload type
                                }
                                @Override
                                public void handleFrame(StompHeaders headers, Object payload) {
                                    try{
                                        Comment comment = (Comment) payload;
                                        handleComment(comment);}
                                    catch(Exception e){
                                        System.err.println(e.getMessage());
                                    }

                                }
                            });
                            sendUserChange(new User(username, 0,true));
                        }
                        @Override
                        public void onFailure(Throwable ex) {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("Connection Error");
                                alert.setHeaderText("Failed to Connect to WebSocket");
                                alert.setContentText(ex.getMessage());
                                alert.showAndWait();
                            });
                        }
                    });
        } catch (Exception e) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Initialization Error");
                alert.setHeaderText("WebSocket Setup Failed");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            });
        }
    }

    public void sendChange(Node newChange) {
        if (stompSession != null && stompSession.isConnected()) {
            System.out.println("sending..");
            stompSession.send("/app/document/" + docId, newChange);
        } else {
            System.err.println("STOMP session not connected");
            synchronized (offlineOperations) {
                offlineOperations.add(newChange);
            }
            }
        }

    public void sendUserChange(User newChange){
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.send("/app/change/" + docId, newChange);
        } else {
            System.err.println("STOMP session not connected");
        }
    }
    public void sendComment(Comment comment) {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.send("/app/comment/" + docId, comment);
        } else {
            System.err.println("STOMP session not connected");
        }
    }

    public long getClock() {
        return System.nanoTime();
    }

    public void handleDisconnect(String username, String docId) {
        synchronized (this) {
            if (!isConnected) return; // Already handling disconnection
            isConnected = false;
            disconnectTime = System.currentTimeMillis();
            System.out.println("Disconnected, starting reconnection attempts...");
            scheduler.scheduleAtFixedRate(() -> attemptReconnect(), 0, RECONNECT_INTERVAL_MS, TimeUnit.MILLISECONDS);
            Platform.runLater(() -> {
                if (onDocumentChange != null) {
                    onDocumentChange.run(); // Update UI to show disconnection
                }
            });
        }
    }
    private void attemptReconnect() {
        synchronized (this) {
            if (isConnected) return;
            if (System.currentTimeMillis() - disconnectTime > RECONNECT_WINDOW_MS) {
                System.err.println("Reconnect window expired");
                scheduler.shutdown();
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Session Expired");
                    alert.setHeaderText("Reconnection Failed");
                    alert.setContentText("Session expired after 5 minutes. Please reconnect manually.");
                    alert.showAndWait();
                });
                return;
            }
            System.out.println("Attempting to reconnect...");
            try {
                connectToWebSocket(username, docId);
            } catch (Exception e) {
                System.err.println("Reconnect failed: " + e.getMessage());
            }
        }
}
class MyStompSessionHandler extends StompSessionHandlerAdapter {
    private final WebSocketController controller;
    private final String username;
    private final String docId;

    public MyStompSessionHandler(WebSocketController controller, String username, String docId) {
        this.controller = controller;
        this.username = username;
        this.docId = docId;
    }
    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        System.out.println("Connected to WebSocket server!");
        // Handle reconnection
        synchronized (controller.offlineOperations) {
            if (!controller.offlineOperations.isEmpty()) {
                for (Node op : controller.offlineOperations) {
                    session.send("/app/document/" + docId, op);
                    System.out.println("Sent queued operation: " + op);
                }
                controller.offlineOperations.clear();
            }
        }
        controller.requestChanges();
    }

    @Override
    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
        System.err.println("An error occurred: " + exception.getMessage());
    }
    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        System.err.println("Transport error: " + exception.getMessage());
        controller.handleDisconnect(username,docId);
    }
}

    private void requestChanges() {
            // Send HTTP request to join session
//                HttpRequest request = HttpRequest.newBuilder()
//                        .uri(URI.create(SERVER_URL + "/grantAccess"))
//                        .header("Content-Type", "application/json")
//                        .POST(HttpRequest.BodyPublishers.ofString("{\"password\":\"" + code + "\"}"))
//                        .build();
//                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//                AccessResponse accessResponse = objectMapper.readValue(response.body(), AccessResponse.class);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            RestTemplate restTemplate = new RestTemplate();
            MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
            converter.setObjectMapper(JacksonConfig.getObjectMapper());
            restTemplate.getMessageConverters()
                    .removeIf(m -> m instanceof MappingJackson2HttpMessageConverter); // Remove default Jackson converter
            restTemplate.getMessageConverters().add(converter);
            ArrayList<String> requestData=new ArrayList<String>();
            requestData.add(joinCode);
            ObjectMapper mapper = new ObjectMapper();


            AccessResponse response=restTemplate.postForObject(SERVER_URL + "/grantAccess/"+username , requestData, AccessResponse.class);
                String docId = response.getDocId();
                boolean accessType = response.isWritePermission();
                Node[] importedNodes = response.getDocumentNodes();
                for (Node n : importedNodes) {
                    System.out.println(n.content);
                    if (n.getOperation() == 0) {
                        this.getDocumentTree().insert(n);
                    }
                    else{
                        this.getDocumentTree().delete(n.getId());
                    }
                }
                this.initializeData(username, docId, response.getConnectedUsers(),joinCode);
                //switchToSessionPage(username, "", "", accessType);
    }
}
