package apt.textserver.service;

import apt.textserver.model.*;
//import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.security.SecureRandom;


@Service
public class DocumentService {
    ConcurrentHashMap<String, Document> documents = new ConcurrentHashMap<>();
    public void addChange(String docId, Node change){
        Document doc = documents.get(docId);
        if (doc != null) {
            doc.addChange(change);
        }
        else {
            System.out.println("Document not found: " + docId);
        }
    }
    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[12]; // 12 bytes = 96-bit entropy
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public CreateResponse createDocument(ArrayList<Node> importFile,String ownerName) {
        Document doc = new Document();
        doc.getConnectedUsers().put(ownerName,0);
        if (importFile != null && !importFile.isEmpty()) {
            doc.setChangesNodes(new ConcurrentLinkedQueue<Node>(importFile));
        }
        String docId = UUID.randomUUID().toString();
        String readPassword = generateRandomPassword();
        String writePassword = generateRandomPassword();
        doc.setId(docId);
        //doc.setReadPassword(BCrypt.hashpw(readPassword, BCrypt.gensalt()));
        //doc.setWritePassword(BCrypt.hashpw(writePassword, BCrypt.gensalt()));
        doc.setReadPassword(readPassword);
        doc.setWritePassword(writePassword);
        documents.put(doc.getId(), doc);
        CreateResponse response = new CreateResponse();
        response.setDocId(doc.getId());
        response.setReadPassword(readPassword);
        response.setWritePassword(writePassword);
        return response;
    }

    public AccessResponse grantAccess(String password,String user){
        AccessResponse response = new AccessResponse();
        for (Document doc : documents.values()){
            //if (BCrypt.checkpw(password, doc.getReadPassword())){
            if(Objects.equals(password, doc.getReadPassword())) {
                response.setDocId(doc.getId());
                response.setWritePermission(false);
                response.setDocumentNodes(doc.getChangesNodes().toArray(new Node[0]));
                if(!doc.getConnectedUsers().containsKey(user)) {
                    doc.getConnectedUsers().put(user, 0);
                    response.setConnectedUsers(doc.getConnectedUsers());
                } else {
                    response.setConnectedUsers(null);
                }
                return response;
                //} else if (BCrypt.checkpw(password, doc.getWritePassword())){
            }else if (Objects.equals(password, doc.getWritePassword())){
                response.setDocId(doc.getId());
                response.setWritePermission(true);
                response.setDocumentNodes(doc.getChangesNodes().toArray(new Node[0]));
                if(!doc.getConnectedUsers().containsKey(user)) {
                    doc.getConnectedUsers().put(user, 0);
                    response.setConnectedUsers(doc.getConnectedUsers());
                } else {
                    response.setConnectedUsers(null);
                }
                return response;
            }
        }
        return null;
    }

    public void changeCursor(String docId, User change) {
        Document doc = documents.get(docId);
        if(doc!=null) {
            if(change.isConnected()){
            doc.getConnectedUsers().put(change.getUserName(), change.getCursorPosition());}
            else{
                doc.getConnectedUsers().remove(change.getUserName());
            }
        }else {
            System.out.println("Document not found: " + docId);
        }
    }
}
