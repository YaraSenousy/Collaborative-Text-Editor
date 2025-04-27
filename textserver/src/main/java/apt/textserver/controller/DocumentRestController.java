package apt.textserver.controller;

import apt.textserver.model.AccessResponse;
import apt.textserver.model.CreateResponse;
import apt.textserver.model.Node;
import apt.textserver.service.DocumentService;

import java.util.ArrayList;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
    public CreateResponse createDocument(@RequestBody ArrayList<Node> importFile) {
        return documentService.createDocument(importFile);
    }
    @PostMapping("/grantAccess")
    public AccessResponse grantAccess(String password) {
        return documentService.grantAccess(password);
    }
}
