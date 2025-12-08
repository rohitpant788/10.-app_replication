package com.rohit.file.repo;

import com.rohit.file.entity.FileMetadata;
import com.rohit.file.entity.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    List<FileMetadata> findByCaseId(Long caseId);

    List<FileMetadata> findByStatus(FileStatus status);

    List<FileMetadata> findByCaseIdAndStatus(Long caseId, FileStatus status);

    @Transactional
    @Modifying
    @Query("UPDATE FileMetadata f SET f.status = :status WHERE f.caseId = :caseId")
    void updateStatusByCaseId(@Param("caseId") Long caseId,
                              @Param("status") String status);
}

