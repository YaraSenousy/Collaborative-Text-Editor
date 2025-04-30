package apt.textclient;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateResponse {
    String docId;
    String readPassword;
    String writePassword;
//    String getDocumentId(){
//        return this.docId;
//    }

}

