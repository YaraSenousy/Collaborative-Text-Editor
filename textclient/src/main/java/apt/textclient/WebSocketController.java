package apt.textclient;

import apt.textclient.User;
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
    private StompSession stompSession;
    private String username;
    private String docId;
    private CRDTTree documentTree = new CRDTTree();
    private ObjectMapper objectMapper = new ObjectMapper();
    private Runnable onDocumentChange;
    private Runnable onUsersChange;
    private ConcurrentHashMap<String,Integer> connectedUsers;


    public void initializeData( String username,String docId,ConcurrentHashMap<String,Integer> connectedUsers) {
        this.username = username;
        this.docId = docId;
        this.connectedUsers=connectedUsers;
        connectToWebSocket(username, docId);
        System.out.println("docId after connecttowebsock "+docId+" username: "+username);
    }
    private void handleReceivedNode(Node newnNode){
        if (newnNode.getOperation() == 0){
            System.out.println("node written "+newnNode.getContent());
            this.documentTree.insert(newnNode);
        }else{
            this.documentTree.delete(newnNode.getId());
        }
        if (onDocumentChange != null) {
            Platform.runLater(onDocumentChange);
        }
    }
    private void handleReceivedChange(User newuser){
        connectedUsers.put(newuser.getUserName(),newuser.getCursorPosition());
        if (onUsersChange != null) {
            Platform.runLater(onUsersChange);
        }
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

            String url = "ws://localhost:8080/ws";
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
            stompSession.send("/app/document/" + docId, newChange);
        } else {
            System.out.println("failed because of sendchange");
            System.err.println("STOMP session not connected");
        }
    }
    public void sendUserChange(User newChange){
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.send("/app/change/" + docId, newChange);
        } else {
            System.out.println("failed because of sendUserchange");
            System.err.println("STOMP session not connected");
        }
    }

    public long getClock() {
        return System.currentTimeMillis();
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
