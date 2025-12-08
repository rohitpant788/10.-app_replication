export interface TempFileUploadResponse {
    fileMetadataId: number;
    docId: number;
    status: string;
}

export interface AttachFilePayload {
    caseId: number;
    fileId: number;
}
