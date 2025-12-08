package com.rohit.file.dto;

import com.rohit.file.entity.FileStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class FileMetadataResponse {
    private Long id;
    private Long caseId;
    private Long docId;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private FileStatus status;
    private LocalDateTime uploadedAt;
}


