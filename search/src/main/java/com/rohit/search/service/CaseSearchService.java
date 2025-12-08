package com.rohit.search.service;

import com.rohit.search.entity.Case;
import com.rohit.search.repository.CaseRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CaseSearchService {

    private final CaseRepository caseRepository;

    public CaseSearchService(CaseRepository caseRepository) {
        this.caseRepository = caseRepository;
    }

    public List<Case> getAllCases() {
        return caseRepository.findAll();
    }
}
