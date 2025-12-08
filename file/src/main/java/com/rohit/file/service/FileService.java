package com.rohit.file.service;

import com.rohit.file.dto.FileMetadataResponse;
import com.rohit.file.dto.FileUploadResponse;
import com.rohit.file.entity.Doc;
import com.rohit.file.entity.FileMetadata;
import com.rohit.file.entity.FileStatus;
import com.rohit.file.repo.DocRepository;
import com.rohit.file.repo.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

@Service
public class FileService {

    private final FileMetadataRepository metadataRepository;
    private final DocRepository docRepository;
    private final WebClient webClient;
    private final String dataServiceBaseUrl;

    public FileService(FileMetadataRepository fileMetadataRepository,
                       DocRepository docRepository,
                       WebClient webClient,
                       @Value("${data.service.base-url}") String dataServiceBaseUrl) {
        this.metadataRepository = fileMetadataRepository;
        this.docRepository = docRepository;
        this.webClient = webClient;
        this.dataServiceBaseUrl = dataServiceBaseUrl;
    }

    // ✓ 1. Upload file (TEMP or FINAL based on case existence)
    @Transactional
    public FileUploadResponse uploadFile(MultipartFile file, Long caseId, String uploadedBy) throws IOException {

        boolean caseExists = checkCaseExists(caseId);

        FileStatus status = caseExists ? FileStatus.FINAL : FileStatus.TEMP;

        // Save BLOB in doc table
        Doc doc = new Doc();
        doc.setContent(file.getBytes());
        doc.setUploadedBy(uploadedBy);
        Doc savedDoc = docRepository.save(doc);

        // Save metadata
        FileMetadata meta = new FileMetadata();
        meta.setDocId(savedDoc.getId());
        meta.setCaseId(caseId);
        meta.setFileName(file.getOriginalFilename());
        meta.setFileSize(file.getSize());
        meta.setContentType(file.getContentType());
        meta.setStatus(status.name()); // Convert enum to String
        meta.setUploadedBy(uploadedBy);

        FileMetadata savedMeta = metadataRepository.save(meta);

        return new FileUploadResponse(savedMeta.getId(), savedDoc.getId(), status);
    }

    public boolean checkCaseExists(Long caseId) {

        String url = dataServiceBaseUrl + "/data/" + caseId + "/exists";

        try {
            Boolean response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();

            return response != null && response;

        } catch (Exception e) {
            System.out.println("Error calling data service: " + e.getMessage());
            return false;
        }
    }


    // ✓ 2. Get document content
    public byte[] getDocument(Long docId) {
        return docRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + docId))
                .getContent();
    }

    // ✓ 3. Get all files for case
    public List<FileMetadataResponse> getFilesByCaseId(Long caseId) {
        return metadataRepository.findByCaseId(caseId)
                .stream()
                .map(meta -> new FileMetadataResponse(
                        meta.getId(),
                        meta.getCaseId(),
                        meta.getDocId(),
                        meta.getFileName(),
                        meta.getFileSize(),
                        meta.getContentType(),
                        FileStatus.valueOf(meta.getStatus()), // Convert String to enum
                        meta.getUploadedAt()))
                .toList();
    }

    // ✓ 4. When case is submitted → convert TEMP → FINAL
    @Transactional
    public void finalizeFiles(Long caseId) {
        metadataRepository.updateStatusByCaseId(caseId, FileStatus.FINAL.name());
    }

    // ✓ 5. Delete file (optional)
    @Transactional
    public void deleteFile(Long metadataId) {
        FileMetadata meta = metadataRepository.findById(metadataId)
                .orElseThrow(() -> new RuntimeException("File metadata not found with id: " + metadataId));

        docRepository.deleteById(meta.getDocId());
        metadataRepository.delete(meta);
    }
}
