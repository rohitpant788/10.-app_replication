package com.rohit.search.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cases")
@Data
public class Case {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;

    @Column(length = 10, nullable = false)
    private String country;

    private BigDecimal amount;

    @Column(name = "reporter_name")
    private String reporterName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
