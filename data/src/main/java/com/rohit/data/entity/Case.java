package com.rohit.data.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cases")
@Data
@Getter
@Setter
public class Case {

    @Id
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(length = 10, nullable = false)
    private String country;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "reporter_name", nullable = false)
    private String reporterName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdDate = LocalDateTime.now();

}
