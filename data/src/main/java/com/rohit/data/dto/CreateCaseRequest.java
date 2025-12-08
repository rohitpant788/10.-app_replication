package com.rohit.data.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Data
@Getter
@Setter
public class CreateCaseRequest {
    private Long id;
    private String title;
    private String description;
    private String country;
    private BigDecimal amount;
    private String reporterName;
}
