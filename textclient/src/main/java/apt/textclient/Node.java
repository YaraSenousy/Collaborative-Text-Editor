package apt.textclient;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.TreeSet;
import java.util.Comparator;
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Node {
    String id;
    final long clock;
    final String parentId;
    final char content;
    final String userId;
    boolean isDeleted;
    @JsonIgnore
    TreeSet<Node> children;
    int operation; // 0 for insert, 1 for delete
    public Node() {
        this.userId = null;
        this.clock = 0;
        this.id = null;
        this.parentId = null;
        this.content = '\0';
        this.operation = 0;
        this.isDeleted = false;
        this.children = new TreeSet<>(
                Comparator.comparingLong(Node::getClock).reversed()
                        .thenComparing(Node::getUserId)
        );
    }
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


}

