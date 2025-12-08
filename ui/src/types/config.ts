export interface MicroserviceConfig {
    baseUrl: string;
    description: string;
}

export interface AppConfig {
    environment: "local" | "production";
    microservices: {
        data: MicroserviceConfig;
        refdata: MicroserviceConfig;
        file: MicroserviceConfig;
        search: MicroserviceConfig;
    };
}
