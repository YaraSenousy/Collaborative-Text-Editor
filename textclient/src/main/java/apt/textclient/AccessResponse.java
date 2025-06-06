package apt.textclient;



import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Getter @Setter
public class AccessResponse
{
    String docId;
    boolean writePermission; //true for write access, false for read access
    Node [] documentNodes;
    ConcurrentHashMap<String,User> connectedUsers;
    ConcurrentHashMap<String,Comment> comments;
}

