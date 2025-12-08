import { refDataClient } from "./httpClient";
import { Country } from "../types/refData";

export const getCountries = async (): Promise<Country[]> => {
    const response = await refDataClient.get<Country[]>("/refdata/countries");
    return response.data;
};
