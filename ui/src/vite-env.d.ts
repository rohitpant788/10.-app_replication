/// <reference types="vite/client" />

interface ImportMetaEnv {
    readonly VITE_DATA_API_URL?: string;
    readonly VITE_REFDATA_API_URL?: string;
    readonly VITE_FILE_API_URL?: string;
    readonly VITE_SEARCH_API_URL?: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}
