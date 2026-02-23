import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { CssVarsProvider, extendTheme } from '@mui/joy/styles'
import CssBaseline from '@mui/joy/CssBaseline'
import './index.css'
import App from './App.tsx'

const theme = extendTheme({
  colorSchemes: {
    dark: {
      palette: {
        background: {
          body: '#0a0e1a',
          surface: '#111827',
        },
      },
    },
  },
  fontFamily: {
    body: "'Inter', 'system-ui', sans-serif",
    display: "'Inter', 'system-ui', sans-serif",
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <CssVarsProvider theme={theme} defaultMode="dark">
      <CssBaseline />
      <App />
    </CssVarsProvider>
  </StrictMode>,
)
