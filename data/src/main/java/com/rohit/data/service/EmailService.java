package com.rohit.data.service;

import com.rohit.data.entity.Case;

public interface EmailService {
    void sendCaseCreatedEmail(Case caseEntity);
}
