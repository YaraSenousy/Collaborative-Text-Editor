package apt.textserver.model;

import lombok.Getter;
import lombok.Setter;
 
@Getter @Setter
public class AccessResponse
{
    String docId;
    boolean writePermission;
    Node [] documentNodes;
}
