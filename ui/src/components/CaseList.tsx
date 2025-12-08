import React from "react";
import { CaseDto } from "../types/case";

interface CaseListProps {
    cases: CaseDto[];
    loading: boolean;
    error?: string;
}

export const CaseList: React.FC<CaseListProps> = ({ cases, loading, error }) => {
    const formatDate = (dateString: string): string => {
        try {
            const date = new Date(dateString);
            const options: Intl.DateTimeFormatOptions = {
                day: "2-digit",
                month: "short",
                year: "numeric",
                hour: "2-digit",
                minute: "2-digit",
                hour12: false
            };
            return new Intl.DateTimeFormat("en-GB", options).format(date);
        } catch (error) {
            return dateString;
        }
    };

    if (loading) {
        return <div className="case-list-loading">Loading cases...</div>;
    }

    if (error) {
        return <div className="case-list-error">Error: {error}</div>;
    }

    if (cases.length === 0) {
        return <div className="case-list-empty">No cases found. Create your first case above!</div>;
    }

    return (
        <div className="case-list">
            <h2>All Cases</h2>
            <table className="case-table">
                <thead>
                    <tr>
                        <th>Case ID</th>
                        <th>Title</th>
                        <th>Country</th>
                        <th>Amount</th>
                        <th>Reporter Name</th>
                        <th>Created At</th>
                    </tr>
                </thead>
                <tbody>
                    {cases.map((caseItem) => (
                        <tr key={caseItem.id}>
                            <td>{caseItem.id}</td>
                            <td>{caseItem.title}</td>
                            <td>{caseItem.country}</td>
                            <td>{caseItem.amount.toFixed(2)}</td>
                            <td>{caseItem.reporterName}</td>
                            <td>{formatDate(caseItem.createdAt)}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
};
