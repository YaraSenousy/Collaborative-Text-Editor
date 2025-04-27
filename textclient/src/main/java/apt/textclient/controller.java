package apt.textclient;

import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

public class controller {
    private StompSession stompSession;
    private String username;
    private String docId;
    private CRDTTree documentTree;

    public void initializeData( String username, String docId) {
        this.username = username;
        this.docId = docId;
        this.documentTree = new CRDTTree();
        connectToWebSocket(username, docId);
        // Subscribe to the chat room
        String topic = "/topic/document/" + docId;
        stompSession.subscribe(topic, new StompFrameHandler() {

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Node.class; // Expected payload type
            }
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                Node change = (Node) payload;
                handleReceivedNode(change);

            }
        });
    }
    private void handleReceivedNode(Node newnNode){
        if (newnNode.getOperation() == 0){
            this.documentTree.insert(newnNode);
        }else{
            this.documentTree.delete(newnNode.getId());
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
                            // Initialize subscriptions here if needed
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
    private void handleDocChange(long clock,String parentId, char content,int operation) {
        /*String content = messageField.getText().trim();
        if (!content.isEmpty()) {
            // Send the message to the WebSocket server
            String destination = "/app/chat/" + docId;
            ChatMessage message = new ChatMessage();
            message.setUsername(username);
            message.setContent(content);
            stompSession.send(destination, message);
            messageField.clear();
        }*/
        //handling ba2a ezay hangeeb 7agat el node el gedeeda 3ashan ne intialise newChange
        Node newChange= new Node(this.username,clock,parentId,content,operation);
        String destination = "/app/document/" + docId;
        stompSession.send(destination, newChange);
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
