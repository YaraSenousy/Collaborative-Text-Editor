package apt.textserver.model;

import lombok.Getter;
import lombok.Setter;
 
@Getter @Setter
public class CreateResponse {
    String docId;
    String readPassword;
    String writePassword;
}
