import java.util.TreeSet;
import java.util.Comparator;
public class Node {
    final String id;
    final long clock;
    final String parentId;
    final char content;
    final String userId;
    boolean isDeleted;
    TreeSet<Node> children;
    int operation; // 0 for insert, 1 for delete

    public Node(String userId, long clock, String parentId, char content,int operation) {
        this.operation = operation;
        this.userId = userId;
        this.clock = clock;
        this.id = userId + "-" + clock;
        this.parentId = parentId;
        this.content = content;
        this.children = new TreeSet<>(
            Comparator.comparingLong(Node::getClock).reversed()
                .thenComparing(Node::getUserId)
        );
    }

    // Getters
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public long getClock() { return clock; }
    public boolean isDeleted() { return isDeleted; }
    public TreeSet<Node> getChildren() { return children; }
    public String getParentId() { return parentId; }
    public char getContent() { return content; }
    public int getOperation() { return operation; }

    public void markDeleted() { isDeleted = true; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return id.equals(node.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}

