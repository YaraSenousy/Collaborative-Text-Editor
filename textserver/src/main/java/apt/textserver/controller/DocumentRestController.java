package apt.textserver.controller;

import apt.textserver.model.AccessResponse;
import apt.textserver.model.CreateResponse;
import apt.textserver.model.Node;
import apt.textserver.service.DocumentService;

import java.util.ArrayList;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.HashMap;

@RestController
public class DocumentRestController {
    private final DocumentService documentService;
    public DocumentRestController(DocumentService documentService) {
        this.documentService = documentService;
    }
    @GetMapping("/ip")
    public String hello() {
        return "Hello, World!";
    }
    @PostMapping("/createDocument")
    public CreateResponse createDocument(@RequestBody(required = false) ArrayList<Node> importFile) {
        if (importFile == null){
            importFile = new ArrayList<>();
        }
        return documentService.createDocument(importFile);
    }
    @PostMapping("/grantAccess")
    public AccessResponse grantAccess(@RequestBody Map<String, String> request) {
        String password = request.get("password"); // Extract password from the JSON body
        AccessResponse response = documentService.grantAccess(password);
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        return response;
    }
}
