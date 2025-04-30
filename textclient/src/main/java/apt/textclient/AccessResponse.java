package apt.textclient;

public class AccessResponse
{
    String docId;
    boolean writePermission;
    Node [] documentNodes;
    String getDocumentId(){
        return this.docId;
    }

    boolean getAccessType(){
        return  this.writePermission;
    }
}
