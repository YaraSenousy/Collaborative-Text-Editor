package apt.textclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;
import lombok.Setter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.stomp.*;
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
    private StompSession stompSession;
    private String username;
    private String docId;
    private CRDTTree documentTree = new CRDTTree();
    private ObjectMapper objectMapper = new ObjectMapper();
    private Runnable onDocumentChange;
    private Runnable onUsersChange;
    private ConcurrentHashMap<String, User> connectedUsers;
    private Runnable onCommentChange;
    private ConcurrentHashMap<String,Comment> comments;

    public void initializeData( String username,String docId,ConcurrentHashMap<String,User> connectedUsers,ConcurrentHashMap<String,Comment> comments) {
        this.username = username;
        this.comments = comments;
        this.docId = docId;
        this.connectedUsers=connectedUsers;
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
            StompSessionHandler sessionHandler = new MyStompSessionHandler();

            // Add connection callback
            stompClient.connect(url, sessionHandler)
                    .addCallback(new ListenableFutureCallback<StompSession>() {
                        @Override
                        public void onSuccess(StompSession session) {
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
}
class MyStompSessionHandler extends StompSessionHandlerAdapter {
    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        System.out.println("Connected to WebSocket server!");
    }

    @Override
    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
        System.err.println("An error occurred: " + exception.getMessage());
    }
}
