import { useState, useEffect } from "react";
import { CreateCaseForm } from "./components/CreateCaseForm";
import { CaseList } from "./components/CaseList";
import { getAllCases } from "./api/caseApi";
import { CaseDto } from "./types/case";
import "./App.css";

function App() {
    const [cases, setCases] = useState<CaseDto[]>([]);
    const [loadingCases, setLoadingCases] = useState(false);
    const [casesError, setCasesError] = useState("");
    const [showCreateForm, setShowCreateForm] = useState(false);

    useEffect(() => {
        loadCases();
    }, []);

    const loadCases = async () => {
        setLoadingCases(true);
        setCasesError("");
        try {
            const data = await getAllCases();
            setCases(data);
        } catch (error) {
            setCasesError("Failed to load cases. Please refresh the page.");
        } finally {
            setLoadingCases(false);
        }
    };

    const handleCaseCreated = () => {
        loadCases();
        setShowCreateForm(false);
    };

    return (
        <div className="app">
            <header className="app-header">
                <h1>Case Portal</h1>
            </header>

            <main className="app-main">
                <section className="create-case-section">
                    <button
                        className="create-case-btn"
                        onClick={() => setShowCreateForm(!showCreateForm)}
                    >
                        {showCreateForm ? "Hide Form" : "Create Case"}
                    </button>

                    {showCreateForm && <CreateCaseForm onCaseCreated={handleCaseCreated} />}
                </section>

                <section className="case-list-section">
                    <CaseList cases={cases} loading={loadingCases} error={casesError} />
                </section>
            </main>
        </div>
    );
}

export default App;
