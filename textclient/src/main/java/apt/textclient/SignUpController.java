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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
//bashoof haga

import java.util.Map;
import java.util.HashMap;



public class SignUpController {
    @FXML
    private TextField usernameField;
    @FXML
    private TextField sessionCodeField;

    private WebSocketController wsController;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SERVER_URL = "http://localhost:8080";
    private static final RestTemplate restTemplate;
    static {
        restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
        restTemplate.getMessageConverters().add(new StringHttpMessageConverter(StandardCharsets.UTF_8));
    }
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

                // Configure RestTemplate for JSON
                //restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter());

                // Prepare request entity
                HttpEntity<String> request = new HttpEntity<>("[]", headers);

                // Call /createDocument endpoint
                CreateResponse response = restTemplate.postForObject(
                        SERVER_URL + "/createDocument",
                        request,
                        CreateResponse.class
                );

                // Process response
                String docId = response.getDocId();
                System.out.println("Write Password: " + response.getWritePassword());
                System.out.println("Read Password: " + response.getReadPassword());
                wsController.initializeData(username, docId);
                switchToSessionPage(username, docId, true);
            } catch (Exception e) {
                showAlert("Error", e.getMessage());
            }
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
                    List<Node> nodes = wsController.getDocumentTree().importFile(username, wsController.getClock(), file.getAbsolutePath());
                    if (nodes == null) {
                        showAlert("Error", "Failed to import file.");
                        return;
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
                    CreateResponse createResponse = restTemplate.postForObject(SERVER_URL + "/createDocument", nodes, CreateResponse.class);
                    String docId = createResponse.getDocId();
                    wsController.initializeData(username,docId);
                    nodes.forEach(wsController::sendChange); // Send nodes to WebSocket for synchronization
                    switchToSessionPage(username, docId, true);
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
                // 1. Prepare headers and body
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, String> requestBody = new HashMap<>();
                requestBody.put("password", code);

                // 2. Create HTTP entity
                HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

                // 3. Send POST request
                AccessResponse accessResponse = restTemplate.postForObject(
                        SERVER_URL + "/grantAccess",
                        request,
                        AccessResponse.class
                );

                // 4. Handle response
                if (accessResponse != null) {
                    // Use Lombok-generated getters
                    String docId = accessResponse.getDocId();
                    boolean writePermission = accessResponse.isWritePermission();

                    wsController.initializeData(username, docId);
                    switchToSessionPage(username, docId, writePermission);
                } else {
                    showAlert("Error", "Invalid session code or server error.");
                }
            } catch (Exception e) {
                e.printStackTrace();
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