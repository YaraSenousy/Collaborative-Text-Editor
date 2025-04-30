package apt.textserver.controller;

import apt.textserver.model.AccessResponse;
import apt.textserver.model.CreateResponse;
import apt.textserver.model.Node;
import apt.textserver.service.DocumentService;

import java.util.ArrayList;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    public CreateResponse createDocument(@RequestParam("username") String username, @RequestBody(required = false) ArrayList<Node> importFile) {
        if (importFile == null){
            importFile = new ArrayList<>();
        }
        CreateResponse response = documentService.createDocument(importFile);
        documentService.adduser(response.getDocId(),username);
        return response;
    }
    @PostMapping("/grantAccess")
    public AccessResponse grantAccess(@RequestBody String password) {

        AccessResponse response = documentService.grantAccess(password);
        if (response == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }else{
            return response;
        }
    }
}
