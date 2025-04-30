//package apt.textclient;
//
//import lombok.Getter;
//import lombok.Setter;
//
//@Getter
//@Setter
//public class AccessResponse
//{
//    String docId;
//    boolean writePermission;
//    Node [] documentNodes;
//    String getDocumentId(){
//        return this.docId;
//    }
//
//    boolean getAccessType(){
//        return  this.writePermission;
//    }
//}
//
//package apt.textclient;
//
//import com.fasterxml.jackson.annotation.JsonProperty;
//import lombok.Getter;
//import lombok.Setter;
//
//@Getter
//@Setter
//public class AccessResponse {
//    private String docId;          // Maps to JSON key "docId"
//    private boolean writePermission; // Maps to JSON key "writePermission"
//    private Node[] documentNodes;   // Maps to JSON key "documentNodes"
//
//    // (Optional) If you need custom getters, use @JsonProperty
//    @JsonProperty("docId")
//    public String getDocumentId() {
//        return this.docId;
//    }
//
//    @JsonProperty("writePermission")
//    public boolean getAccessType() {
//        return this.writePermission;
//    }
//}

package apt.textclient;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccessResponse {
    private String docId;          // Maps to JSON key "docId"
    private boolean writePermission; // Maps to "writePermission"
    private Node[] documentNodes;  // Maps to "documentNodes"
}