package apt.textserver.service;

import apt.textserver.model.*;
//import org.springframework.security.crypto.bcrypt.BCrypt;
import com.sun.javafx.geom.Rectangle;
import javafx.scene.paint.Color;
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
        User user= new User(ownerName,0,true);
        user.setColor(generateColor());
        //client will create the cursor rect
        doc.getConnectedUsers().put(ownerName,user);
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
        response.setOwner(user);
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
                    User newuser=new User(user,0,true);
                    newuser.setColor(generateColor());
                    doc.getConnectedUsers().put(user, newuser);
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
                    User newuser=new User(user,0,true);
                    newuser.setColor(generateColor());
                    doc.getConnectedUsers().put(user, newuser);
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
        if (doc != null) {
            if (change.isConnected()) {
                User existingUser = doc.getConnectedUsers().get(change.getUserName());
                if (existingUser != null) {
                    // Update existing user’s cursor position and connection status
                    existingUser.setCursorPosition(change.getCursorPosition());
                    //existingUser.setIsConnected(change.isConnected());
                    existingUser.setConnected(change.isConnected());
                    if (existingUser.getColor() == null) {
                        existingUser.setColor(generateColor()); // Assign new color if null
                        System.out.println("Assigned new color for existing user " + change.getUserName() + ": " + existingUser.getColor());
                    }
                    doc.getConnectedUsers().put(change.getUserName(), existingUser);
                } else {
                    if (change.getColor() == null) {
                        change.setColor(generateColor());
                        System.out.println("Assigned new color for new user " + change.getUserName() + ": " + change.getColor());
                    }
                    doc.getConnectedUsers().put(change.getUserName(), change);
                }
            } else {
                doc.getConnectedUsers().remove(change.getUserName());
                System.out.println("Removed user " + change.getUserName() + " from document " + docId);
            }
        } else {
            System.out.println("Document not found: " + docId);
        }
    }

    private String generateColor() {
        Random random = new Random();
        float hue = random.nextInt(360);
        float saturation = 0.4f + random.nextFloat() * 0.2f;
        float brightness = 0.3f + random.nextFloat() * 0.3f;

        Color fxColor = Color.hsb(hue, saturation, brightness);
        return String.format("#%02X%02X%02X",
                (int)(fxColor.getRed() * 255),
                (int)(fxColor.getGreen() * 255),
                (int)(fxColor.getBlue() * 255)
        );
    }
}
