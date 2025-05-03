package apt.textclient;



import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

@Getter @Setter
public class AccessResponse
{
    String docId;
    boolean writePermission; //true for write access, false for read access
    Node [] documentNodes;
    ArrayList<String> connectedUsers;
}

