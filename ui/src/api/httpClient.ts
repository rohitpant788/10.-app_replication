import axios from "axios";
import configLocal from "../../config-local.json";
import configProd from "../../config-prod.json";
import { AppConfig } from "../types/config";

// Select configuration based on environment
// In development (npm run dev), use config-local.json
// In production build (npm run build), use config-prod.json
const isDevelopment = import.meta.env.MODE === 'development' || import.meta.env.DEV;
const config = (isDevelopment ? configLocal : configProd) as AppConfig;

// Data microservice client (port 9090 in local)
export const dataClient = axios.create({
    baseURL: config.microservices.data.baseUrl,
    headers: {
        "Content-Type": "application/json"
    }
});

// RefData microservice client (port 9092 in local)
export const refDataClient = axios.create({
    baseURL: config.microservices.refdata.baseUrl,
    headers: {
        "Content-Type": "application/json"
    }
});

// File microservice client (port 9091 in local)
export const fileClient = axios.create({
    baseURL: config.microservices.file.baseUrl,
    headers: {
        "Content-Type": "application/json"
    }
});

// Search microservice client (port 9093 in local)
export const searchClient = axios.create({
    baseURL: config.microservices.search.baseUrl,
    headers: {
        "Content-Type": "application/json"
    }
});

// Deprecated: Keep for backward compatibility during migration
export const httpClient = dataClient;

// Export config for debugging/logging purposes
export const appConfig = config;
