# PrivSex Links

Landing oficial da plataforma PrivSex, separada da antiga aplicação de links de modelo.

## Estrutura

- `frontend`: Vue 3 + Vite. Pode ser publicado na Vercel definindo `frontend` como Root Directory.
- `backend`: Java 21 + Spring Boot. Deve ser publicado em um serviço que execute Java/Docker.

O frontend não contém chave de serviço. O avatar e a posição do avatar são lidos do Supabase exclusivamente pelo backend Java. A posição editável é protegida por cookie HttpOnly assinado e não usa localStorage.

## Desenvolvimento local

Frontend:

```powershell
cd frontend
npm install
$env:VITE_API_BASE_URL = 'http://localhost:8080'
npm run dev
```

Backend: defina as variáveis de `backend/.env.example` no ambiente e execute:

```powershell
cd backend
mvn spring-boot:run
```

Abra `http://localhost:5173/juliasales`. A área administrativa fica em `/gestao` e exige `ADMIN_PASSWORD`.

## Deploy

1. Publique o `frontend` na Vercel e configure `VITE_API_BASE_URL` com a URL pública do backend.
2. Publique o `backend` usando o `Dockerfile` ou um serviço Java.
3. No backend, configure `SUPABASE_SERVICE_KEY`, `ADMIN_PASSWORD`, `ADMIN_SESSION_SECRET`, `ALLOWED_ORIGINS` e `COOKIE_SECURE=true`.
4. Nunca coloque `SUPABASE_SERVICE_KEY` em variáveis `VITE_*` nem no código do navegador.

