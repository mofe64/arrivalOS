# ArrivalOS Frontend

React/TanStack frontend for ArrivalOS, the Gbèjà Global Security airport arrival command interface.

## Stack

- Vite
- React
- TypeScript
- TanStack Router
- TanStack Query

## Runtime

Use Node `>=20.19` or `>=22.12`. Vite 8 and ESLint 10 require newer Node runtime APIs.

## Environment

Production API configuration:

```bash
VITE_API_BASE_URL=https://your-arrivalos-api.example.com/api
```

Local development defaults to `/api`, with Vite proxying to `http://localhost:8080`.

Fixtures are disabled by default. For local UI work only:

```bash
VITE_ENABLE_FIXTURES=true npm run dev
```

Do not enable fixtures in production. Missing backend endpoints should render loading, error, or empty states.

## Run

```bash
npm install
npm run dev
```

## Verify

```bash
npm run lint
npm run build
```
