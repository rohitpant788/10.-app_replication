import { fileClient } from "./httpClient";
import { TempFileUploadResponse } from "../types/file";

export const uploadTempFile = async (file: File, uploadedBy: string, caseId: number): Promise<TempFileUploadResponse> => {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("uploadedBy", uploadedBy);
    formData.append("caseId", caseId.toString());

    const response = await fileClient.post<TempFileUploadResponse>(
        "/file/upload",
        formData,
        {
            headers: {
                "Content-Type": "multipart/form-data"
            }
        }
    );

    return response.data;
};

export const finalizeCaseFiles = async (caseId: number): Promise<void> => {
    await fileClient.post(
        "/file/finalize",
        { caseId },
        {
            headers: {
                "Content-Type": "application/json"
            }
        }
    );
};
