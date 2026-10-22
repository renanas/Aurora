import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import App from './App'
import { dropSession } from './features/auth/api'
import { ApiError } from './shared/api/client'

/** Sessão vencida em qualquer chamada: apaga os dados da tela e volta para o login. */
function handleError(client: QueryClient, error: Error) {
  if (error instanceof ApiError && error.status === 401 && error.code === 'UNAUTHENTICATED') {
    dropSession(client)
  }
}

const queryClient: QueryClient = new QueryClient({
  queryCache: new QueryCache({ onError: (error) => handleError(queryClient, error) }),
  mutationCache: new MutationCache({ onError: (error) => handleError(queryClient, error) }),
  defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: true },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
