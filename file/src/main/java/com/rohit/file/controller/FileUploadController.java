package com.rohit.file.controller;

import com.rohit.file.dto.FileMetadataResponse;
import com.rohit.file.dto.FileStatusUpdateRequest;
import com.rohit.file.dto.FileUploadResponse;
import com.rohit.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileService fileService;

    // ✓ Upload file
    @PostMapping("/upload")
    public FileUploadResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caseId", required = false) Long caseId,
            @RequestParam("uploadedBy") String uploadedBy
    ) throws Exception {
        return fileService.uploadFile(file, caseId, uploadedBy);
    }

    // ✓ Get content
    @GetMapping("/content/{docId}")
    public ResponseEntity<byte[]> getDocument(@PathVariable Long docId) {
        byte[] content = fileService.getDocument(docId);
        return ResponseEntity.ok()
                .header("Content-Type", "application/octet-stream")
                .body(content);
    }

    // ✓ List files by caseId
    @GetMapping("/case/{caseId}")
    public List<FileMetadataResponse> list(@PathVariable Long caseId) {
        return fileService.getFilesByCaseId(caseId);
    }

    // ✓ Change TEMP → FINAL when case submitted
    @PostMapping("/finalize")
    public ResponseEntity<String> finalizeFiles(@RequestBody FileStatusUpdateRequest req) {
        fileService.finalizeFiles(req.getCaseId());
        return ResponseEntity.ok("Files updated to FINAL");
    }

    // Delete file (optional)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) {
        fileService.deleteFile(id);
        return ResponseEntity.noContent().build();
    }
}

