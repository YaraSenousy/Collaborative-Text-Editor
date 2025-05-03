package apt.textserver.model;

import java.util.ArrayList;
import java.util.List;
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
    ArrayList<String> connectedUsers=new ArrayList<>();

    public void addChange(Node change) {
        changesNodes.add(change);
    }
}
