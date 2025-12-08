import { dataClient, searchClient } from "./httpClient";
import { CaseDto, CreateCasePayload } from "../types/case";

export const getNextCaseId = async (): Promise<number> => {
    const response = await dataClient.get<number>("/data/cases/next-id");
    return response.data;
};

export const createCase = async (payload: CreateCasePayload): Promise<CaseDto> => {
    const response = await dataClient.post<CaseDto>("/data/cases", payload);
    return response.data;
};

export const getAllCases = async (): Promise<CaseDto[]> => {
    const response = await searchClient.get<CaseDto[]>("/search/cases");
    return response.data;
};
