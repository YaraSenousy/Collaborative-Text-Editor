package apt.textclient;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

public class SignUpController {
    @FXML
    private TextField usernameField;
    @FXML
    private TextField sessionCodeField;

    private WebSocketController wsController;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SERVER_URL = "http://localhost:8080";
    private static final RestTemplate restTemplate = new RestTemplate();
    public void initialize() {
        wsController = new WebSocketController();
    }

    @FXML
    private void startNewDocument() {
        if (validateUsername()) {
            String username = usernameField.getText();
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<String> request = new HttpEntity<>("[]", headers);
                RestTemplate restTemplate = new RestTemplate();
                restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter());

                CreateResponse response = restTemplate.postForObject(
                        SERVER_URL + "/createDocument",
                        request,
                        CreateResponse.class
                );
                String docId = response.getDocId();
                System.out.println(response.getWritePassword());
                System.out.println(response.getReadPassword());
                wsController.initializeData(username,docId);
                switchToSessionPage(username, docId, true);
            } catch (Exception e) {
                showAlert("Error", e.getMessage());
            }
            //try {
                // Send HTTP request to create a new document
//                HttpRequest request = HttpRequest.newBuilder()
//                        .uri(URI.create(SERVER_URL + "/createDocument"))
//                        .header("Content-Type", "application/json")
//                        .POST(HttpRequest.BodyPublishers.ofString("[]")) // Empty node list for new document
//                        .build();
//                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//                CreateResponse createResponse = objectMapper.readValue(response.body(), CreateResponse.class);
                //CreateResponse createResponse = restTemplate.postForObject(SERVER_URL + "/createDocument", null, CreateResponse.class);
                //String docId = createResponse.getDocumentId();
                //wsController.initializeData(username,docId);
                //switchToSessionPage(username, docId, true);
//            } catch (IOException | InterruptedException e) {
//                showAlert("Error", "Failed to create document: " + e.getMessage());
//            }
        }
    }

    @FXML
    private void uploadFile() {
        if (validateUsername()) {
            String username = usernameField.getText();
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
            File file = fileChooser.showOpenDialog(usernameField.getScene().getWindow());
            if (file != null) {
//                try {

                // Import file to create Node list
                ArrayList<Node> nodes = wsController.getDocumentTree().importFile(username, wsController.getClock(), file.getAbsolutePath());
                if (nodes == null) {
                    showAlert("Error", "Failed to import file.");
                    return;
                }
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    RestTemplate restTemplate = new RestTemplate();
                    restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
                    ArrayList<Node> requestData=new ArrayList<Node>();
                    requestData.addAll(nodes);
                    ObjectMapper mapper = new ObjectMapper();
                    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(nodes));
                    CreateResponse response=restTemplate.postForObject(SERVER_URL + "/createDocument" , requestData, CreateResponse.class);

//
//                    CreateResponse response = restTemplate.postForObject(
//                            SERVER_URL + "/createDocument",
//                            request,
//                            CreateResponse.class
//                    );
                    String docId = response.getDocId();
                    System.out.println(response.getWritePassword());
                    System.out.println(response.getReadPassword());
                    wsController.initializeData(username,docId);
                    //nodes.forEach(wsController::sendChange);
                    switchToSessionPage(username, docId, true);
                } catch (Exception e) {
                    showAlert("Error", e.getMessage());
                }
//                    // Send HTTP request to create document with nodes
//                    String nodesJson = objectMapper.writeValueAsString(nodes);
//                    HttpRequest request = HttpRequest.newBuilder()
//                            .uri(URI.create(SERVER_URL + "/createDocument"))
//                            .header("Content-Type", "application/json")
//                            .POST(HttpRequest.BodyPublishers.ofString(nodesJson))
//                            .build();
//                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//                    CreateResponse createResponse = objectMapper.readValue(response.body(), CreateResponse.class);
                //CreateResponse createResponse = restTemplate.postForObject(SERVER_URL + "/createDocument", nodes, CreateResponse.class);
//                    String docId = response.getDocId();
//                    wsController.initializeData(username,docId);
//                    nodes.forEach(wsController::sendChange); // Send nodes to WebSocket for synchronization
//                    switchToSessionPage(username, docId, true);
//                } catch (IOException | InterruptedException e) {
//                    showAlert("Error", "Failed to create document: " + e.getMessage());
//                }
            }else{
                showAlert("Error", "Couldn't open file");
            }
        }
    }

    @FXML
    private void joinSession() {
        if (validateUsername() && !sessionCodeField.getText().isEmpty()) {
            String username = usernameField.getText();
            String code = sessionCodeField.getText().trim();
            try {
                // Send HTTP request to join session
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SERVER_URL + "/grantAccess"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"password\":\"" + code + "\"}"))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                AccessResponse accessResponse = objectMapper.readValue(response.body(), AccessResponse.class);

                String docId = accessResponse.getDocumentId();
                boolean accessType = accessResponse.getAccessType();
                wsController.initializeData(username,docId);
                switchToSessionPage(username, docId, accessType);
            } catch (IOException | InterruptedException e) {
                showAlert("Error", "Failed to join session: " + e.getMessage());
            }
        } else {
            showAlert("Error", "Please enter a username and session code.");
        }
    }

    private boolean validateUsername() {
        if (usernameField.getText().isEmpty()) {
            showAlert("Error", "Please enter a username.");
            return false;
        }
        return true;
    }

    private void switchToSessionPage(String username, String docId, boolean accessType) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Session.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 800, 600);
            SessionController sessionController = fxmlLoader.getController();
            sessionController.initData(wsController, username, docId, accessType);
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Text Editor - Session: " + docId);
            stage.show();
        } catch (IOException e) {
            showAlert("Error", "Failed to load session page: " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}