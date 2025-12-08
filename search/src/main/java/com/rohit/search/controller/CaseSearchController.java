package com.rohit.search.controller;

import com.rohit.search.entity.Case;
import com.rohit.search.service.CaseSearchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/search")
@CrossOrigin(origins = "*")
public class CaseSearchController {

    private final CaseSearchService caseSearchService;

    public CaseSearchController(CaseSearchService caseSearchService) {
        this.caseSearchService = caseSearchService;
    }

    @GetMapping("/cases")
    public List<Case> getAllCases() {
        return caseSearchService.getAllCases();
    }
}
