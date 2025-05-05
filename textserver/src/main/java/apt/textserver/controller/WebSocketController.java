package apt.textserver.controller;


import apt.textserver.model.Comment;
import apt.textserver.model.Node;

import apt.textserver.model.User;
import apt.textserver.service.DocumentService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;


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

    @MessageMapping("/comment/{docId}")
    @SendTo("/topic/comment/{docId}")
    public Comment handleComment(Comment comment, @DestinationVariable String docId) {
        System.out.println("recieved comment from user: "+comment.getText());
        documentService.changeComment(docId,comment);
        return comment;
    }
}
