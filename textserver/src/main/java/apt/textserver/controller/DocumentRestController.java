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
    @PostMapping("/createDocument/{user}")
    public CreateResponse createDocument(@RequestBody(required = false) ArrayList<Node> importFile, @PathVariable String user) {
        if (importFile == null){
            importFile = new ArrayList<>();
        }
        return documentService.createDocument(importFile,user);
    }
    @PostMapping("/grantAccess/{user}")
    public AccessResponse grantAccess(@RequestBody ArrayList<String> password,@PathVariable String user) {

        AccessResponse response = documentService.grantAccess(password.getFirst(),user);
        if (response == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }else{
            return response;
        }
    }
}
