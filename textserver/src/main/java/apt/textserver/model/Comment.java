package apt.textserver.model;
import lombok.Getter;
import lombok.Setter;

@Setter @Getter
public class Comment {
    private String id;
    private String user;
    private String text;
    private String startNodeId;
    private String endNodeId;
    private int startPosition;
    private int endPosition;
    private int operation;
    private long timestamp;
    public Comment(){};
    public Comment(String user, String text, String startNodeId,String endNodeId,int operation) {
        this.user = user;
        this.text = text;
        this.startNodeId = startNodeId;
        this.endNodeId = endNodeId;
        this.operation = operation;
        this.timestamp = System.currentTimeMillis();
        this.id = user + timestamp;
    }


    @Override
    public String toString() {
        return user + " @ " + startPosition + "-" + endPosition + ": " + text;
    }
}


