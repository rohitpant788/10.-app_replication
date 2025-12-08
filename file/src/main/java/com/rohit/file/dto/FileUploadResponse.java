package com.rohit.file.dto;

import com.rohit.file.entity.FileStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileUploadResponse {
    private Long fileMetadataId;
    private Long docId;
    private FileStatus status;
}

