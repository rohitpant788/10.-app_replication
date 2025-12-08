import React, { useState, useEffect } from "react";
import { getCountries } from "../api/refDataApi";
import { uploadTempFile, finalizeCaseFiles } from "../api/fileApi";
import { createCase, getNextCaseId } from "../api/caseApi";
import { Country } from "../types/refData";
import { TempFileUploadResponse } from "../types/file";

interface CreateCaseFormProps {
    onCaseCreated: () => void;
}

export const CreateCaseForm: React.FC<CreateCaseFormProps> = ({ onCaseCreated }) => {
    const [title, setTitle] = useState("");
    const [description, setDescription] = useState("");
    const [country, setCountry] = useState("");
    const [amount, setAmount] = useState("");
    const [reporterName, setReporterName] = useState("");
    const [file, setFile] = useState<File | null>(null);
    const [tempFile, setTempFile] = useState<TempFileUploadResponse | null>(null);

    const [countries, setCountries] = useState<Country[]>([]);
    const [nextCaseId, setNextCaseId] = useState<number | null>(null);
    const [loadingCountries, setLoadingCountries] = useState(false);
    const [loadingCaseId, setLoadingCaseId] = useState(false);
    const [uploadingFile, setUploadingFile] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");
    const [successMessage, setSuccessMessage] = useState("");
    const [validationErrors, setValidationErrors] = useState<Record<string, string>>({});

    useEffect(() => {
        loadCountries();
        loadNextCaseId();
    }, []);

    const loadCountries = async () => {
        setLoadingCountries(true);
        try {
            const data = await getCountries();
            setCountries(data);
        } catch (error) {
            setErrorMessage("Failed to load countries. Please refresh the page.");
        } finally {
            setLoadingCountries(false);
        }
    };

    const loadNextCaseId = async () => {
        setLoadingCaseId(true);
        try {
            const id = await getNextCaseId();
            setNextCaseId(id);
        } catch (error) {
            setErrorMessage("Failed to load case ID. Please refresh the page.");
        } finally {
            setLoadingCaseId(false);
        }
    };

    const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files.length > 0) {
            setFile(e.target.files[0]);
            setTempFile(null);
        }
    };

    const handleFileUpload = async () => {
        if (!file) {
            setErrorMessage("Please select a file first.");
            return;
        }

        if (nextCaseId === null) {
            setErrorMessage("Case ID not yet generated. Please wait or refresh.");
            return;
        }

        setUploadingFile(true);
        setErrorMessage("");
        try {
            const uploadedBy = reporterName.trim() || "Anonymous";
            const response = await uploadTempFile(file, uploadedBy, nextCaseId);
            setTempFile(response);
            setSuccessMessage(`File uploaded: ${file.name} (TEMP)`);
        } catch (error) {
            setErrorMessage("Failed to upload file. Please try again.");
        } finally {
            setUploadingFile(false);
        }
    };

    const validateForm = (): boolean => {
        const errors: Record<string, string> = {};

        if (!title.trim()) errors.title = "Title is required";
        if (!description.trim()) errors.description = "Description is required";
        if (!country) errors.country = "Country is required";
        if (!amount || parseFloat(amount) <= 0) errors.amount = "Valid amount is required";
        if (!reporterName.trim()) errors.reporterName = "Reporter name is required";

        setValidationErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setErrorMessage("");
        setSuccessMessage("");

        if (!validateForm()) {
            setErrorMessage("Please fix the validation errors.");
            return;
        }

        if (nextCaseId === null) {
            setErrorMessage("Case ID not loaded. Please refresh the page.");
            return;
        }

        setSubmitting(true);
        try {
            const casePayload = {
                id: nextCaseId,
                title,
                description,
                country,
                amount: parseFloat(amount),
                reporterName
            };

            const createdCase = await createCase(casePayload);

            if (tempFile) {
                await finalizeCaseFiles(createdCase.id);
            }

            setSuccessMessage(`Case #${nextCaseId} created successfully!`);
            clearForm();
            onCaseCreated();
        } catch (error) {
            setErrorMessage("Failed to create case. Please try again.");
        } finally {
            setSubmitting(false);
        }
    };

    const clearForm = () => {
        setTitle("");
        setDescription("");
        setCountry("");
        setAmount("");
        setReporterName("");
        setFile(null);
        setTempFile(null);
        setValidationErrors({});
    };

    return (
        <div className="create-case-form">
            <h2>Create New Case</h2>

            {errorMessage && <div className="error-message">{errorMessage}</div>}
            {successMessage && <div className="success-message">{successMessage}</div>}

            <form onSubmit={handleSubmit}>
                <div className="form-group">
                    <label htmlFor="title">Title *</label>
                    <input
                        id="title"
                        type="text"
                        value={title}
                        onChange={(e) => setTitle(e.target.value)}
                        disabled={submitting}
                    />
                    {validationErrors.title && <span className="field-error">{validationErrors.title}</span>}
                </div>

                <div className="form-group">
                    <label htmlFor="description">Description *</label>
                    <textarea
                        id="description"
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        rows={4}
                        disabled={submitting}
                    />
                    {validationErrors.description && <span className="field-error">{validationErrors.description}</span>}
                </div>

                <div className="form-group">
                    <label htmlFor="country">Country *</label>
                    <select
                        id="country"
                        value={country}
                        onChange={(e) => setCountry(e.target.value)}
                        disabled={submitting || loadingCountries}
                    >
                        <option value="">Select a country</option>
                        {countries.map((c) => (
                            <option key={c.code} value={c.code}>
                                {c.name}
                            </option>
                        ))}
                    </select>
                    {validationErrors.country && <span className="field-error">{validationErrors.country}</span>}
                </div>

                <div className="form-group">
                    <label htmlFor="amount">Amount *</label>
                    <input
                        id="amount"
                        type="number"
                        step="0.01"
                        value={amount}
                        onChange={(e) => setAmount(e.target.value)}
                        disabled={submitting}
                    />
                    {validationErrors.amount && <span className="field-error">{validationErrors.amount}</span>}
                </div>

                <div className="form-group">
                    <label htmlFor="reporterName">Reporter Name *</label>
                    <input
                        id="reporterName"
                        type="text"
                        value={reporterName}
                        onChange={(e) => setReporterName(e.target.value)}
                        disabled={submitting}
                    />
                    {validationErrors.reporterName && <span className="field-error">{validationErrors.reporterName}</span>}
                </div>

                <div className="form-group">
                    <label htmlFor="file">File Upload (Optional)</label>
                    <div className="file-upload-section">
                        <input
                            id="file"
                            type="file"
                            onChange={handleFileSelect}
                            disabled={submitting || uploadingFile}
                        />
                        <button
                            type="button"
                            onClick={handleFileUpload}
                            disabled={!file || uploadingFile || submitting}
                            className="upload-btn"
                        >
                            {uploadingFile ? "Uploading..." : "Upload"}
                        </button>
                    </div>
                    {tempFile && (
                        <div className="file-status">
                            File uploaded: {file?.name || "File"} (ID: {tempFile.fileMetadataId}) ({tempFile.status})
                        </div>
                    )}
                </div>

                <div className="form-actions">
                    <button type="submit" disabled={submitting || loadingCaseId} className="submit-btn">
                        {submitting ? "Creating..." : "Create Case"}
                    </button>
                    <button type="button" onClick={clearForm} disabled={submitting} className="cancel-btn">
                        Clear
                    </button>
                </div>
            </form>
        </div>
    );
};
