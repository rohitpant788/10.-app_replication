export interface CaseDto {
    id: number;
    title: string;
    description: string;
    country: string;
    amount: number;
    reporterName: string;
    createdAt: string;
}

export interface CreateCasePayload {
    id: number;
    title: string;
    description: string;
    country: string;
    amount: number;
    reporterName: string;
}
