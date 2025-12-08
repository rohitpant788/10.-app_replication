package com.rohit.data.service;

import com.rohit.data.dto.CreateCaseRequest;
import com.rohit.data.entity.Case;
import com.rohit.data.repository.CaseRepository;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class CaseService {

    private final CaseRepository caseRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;

    public Case createCase(CreateCaseRequest request) {

        Case c = new Case();
        c.setId(request.getId());
        c.setTitle(request.getTitle());
        c.setDescription(request.getDescription());
        c.setCountry(request.getCountry());
        c.setAmount(request.getAmount());
        c.setReporterName(request.getReporterName());

        Case savedCase = caseRepository.save(c);
        emailService.sendCaseCreatedEmail(savedCase);

        return savedCase;
    }

    public Long getNextCaseId() {
        return jdbcTemplate.queryForObject("SELECT nextval('cases_id_seq')", Long.class);
    }

    public boolean caseExists(Long caseId) {
        return caseRepository.existsById(caseId);
    }
}
