# app-ui

A React + TypeScript + Vite application for managing app cases.

## Setup

1. Copy environment file:
   ```bash
   cp .env.development.example .env
   ```

2. Update `.env` with your API base URL:
   ```
   VITE_API_BASE_URL=http://localhost:8080/api
   ```

3. Install dependencies:
   ```bash
   npm install
   ```

4. Run development server:
   ```bash
   npm run dev
   ```

## Project Structure

```
src/
  api/              # API client layer
    httpClient.ts   # Axios instance configuration
    caseApi.ts      # Case-related API calls
    refDataApi.ts   # Reference data API calls
    fileApi.ts      # File upload API calls
  components/       # React components
    CreateCaseForm.tsx
    CaseList.tsx
  types/            # TypeScript type definitions
    case.ts
    file.ts
    refData.ts
  App.tsx           # Main application component
  main.tsx          # Application entry point
```

## Features

- **Create Cases**: Form-based case creation with validation
- **File Upload**: TEMP → FINAL file upload flow
- **Case List**: Responsive table showing all cases
- **Country Selection**: Dynamic dropdown populated from RefData service

## API Endpoints

The application expects the following backend endpoints:

- `POST /data/cases` - Create a new case
- `GET /search/cases` - Retrieve all cases
- `GET /refdata/countries` - Get country list
- `POST /file/files/upload-temp` - Upload file as TEMP
- `POST /file/files/attach` - Attach TEMP file to case (makes it FINAL)

## Build

```bash
npm run build
```

## Preview Production Build

```bash
npm run preview
```
