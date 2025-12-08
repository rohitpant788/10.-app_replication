package com.rohit.data.controller;

import com.rohit.data.dto.CreateCaseRequest;
import com.rohit.data.entity.Case;
import com.rohit.data.service.CaseService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/data")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @PostMapping("/cases")
    public ResponseEntity<?> createCase(@RequestBody CreateCaseRequest request) {
        try {
            Case saved = caseService.createCase(request);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to create case: " + e.getMessage());
        }
    }

    @GetMapping("/cases/next-id")
    public ResponseEntity<Long> getNextCaseId() {
        Long nextId = caseService.getNextCaseId();
        return ResponseEntity.ok(nextId);
    }

    // API: GET /cases/{id}/exists
    @GetMapping("/{id}/exists")
    public ResponseEntity<Boolean> caseExists(@PathVariable Long id) {
        boolean exists = caseService.caseExists(id);
        return ResponseEntity.ok(exists);
    }
}
