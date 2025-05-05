package apt.textserver.controller;
import javax.print.Doc;

import apt.textserver.model.User;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import apt.textserver.model.Node;
import apt.textserver.service.DocumentService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Controller
public class WebSocketController {
    DocumentService documentService;
    public WebSocketController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @MessageMapping("/sessionInfo/{docId}")
    @SendTo("/topic/sessionInfo/{docId}")
    public long getSessionInfo(@DestinationVariable String docId) {
        return documentService.getOrCreateSessionStartTime(docId);
    }

    @MessageMapping("/document/{docId}")
    @SendTo("/topic/document/{docId}")
    public Node handleMessage(Node change, @DestinationVariable String docId) {
        // Todo: insert in the list of nodes corresponding to the document (3ayzeen nezawd service)
        System.out.println("Received: "+ change);
        documentService.addChange(docId, change);
        return change;
    }
    @MessageMapping("/change/{docId}")
    @SendTo("/topic/change/{docId}")
    public User handleChange(User change, @DestinationVariable String docId) {
        //TODO: deal with new connections and cursor position changes
        System.out.println("recieved change from user: "+change.getUserName());
        documentService.changeCursor(docId, change);
        return change;
    }
}
