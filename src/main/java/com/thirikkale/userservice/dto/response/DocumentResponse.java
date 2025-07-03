package com.thirikkale.userservice.dto.response;

import com.thirikkale.userservice.model.enums.DocumentStatus;
import com.thirikkale.userservice.model.enums.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {

    private UUID id;
    private UUID driverId;
    private DocumentType documentType;
    private String fileName;
    private Long fileSize;
    private DocumentStatus status;
    private String verificationNotes;
    private UUID verifiedBy;
    private LocalDateTime verifiedAt;
    private LocalDateTime uploadedAt;
}