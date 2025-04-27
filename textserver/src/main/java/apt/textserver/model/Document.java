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

    public void addChange(Node change) {
        changesNodes.add(change);
    }
}
