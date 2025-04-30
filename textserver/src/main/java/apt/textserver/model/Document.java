package apt.textserver.model;

import java.util.concurrent.ConcurrentLinkedQueue;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Document {
    String id;
    String readPassword;
    String writePassword;
    ConcurrentLinkedQueue<Node> changesNodes = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<String> users = new ConcurrentLinkedQueue<String>();

    public void addChange(Node change) {
        changesNodes.add(change);
    }
    public void addUser(String username){users.add(username);}
}
