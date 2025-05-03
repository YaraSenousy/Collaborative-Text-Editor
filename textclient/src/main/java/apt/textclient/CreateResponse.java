package apt.textclient;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

@Getter
@Setter
public class CreateResponse {
    String docId;
    String readPassword;
    String writePassword;
    ArrayList<String> connectedUsers;
//    String getDocumentId(){
//        return this.docId;
//    }

}

