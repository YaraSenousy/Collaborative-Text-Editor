package apt.textserver.controller;
import javax.print.Doc;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import apt.textserver.model.Node;
import apt.textserver.service.DocumentService;


@Controller
public class WebSocketController {
    DocumentService documentService;
    public WebSocketController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @MessageMapping("/document/{docId}")
    @SendTo("/topic/document/{docId}")
    public Node handleMessage(Node change, @DestinationVariable String docId) {
        // Todo: insert in the list of nodes corresponding to the document (3ayzeen nezawd service)
        documentService.addChange(docId, change);
        return change;
    }
}
