import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom'
import { CssVarsProvider, extendTheme } from '@mui/joy/styles'
import CssBaseline from '@mui/joy/CssBaseline'
import Box from '@mui/joy/Box'
import CircularProgress from '@mui/joy/CircularProgress'
import './index.css'
import App from './App.tsx'
import { AuthProvider, useAuth } from './AuthContext.tsx'
import LoginPage from './pages/LoginPage.tsx'
import SignupPage from './pages/SignupPage.tsx'
import ProfilePage from './pages/ProfilePage.tsx'
import AdminPage from './pages/AdminPage.tsx'
import HomePage from './pages/HomePage.tsx'
import AssetPage from './pages/AssetPage.tsx'
import { useOutletContext } from 'react-router-dom'
import type { SupportedCurrency } from './api'

interface AppContext {
  displayCurrency: SupportedCurrency
}

export function useAppContext() {
  return useOutletContext<AppContext>()
}

function HomeWrapper() {
  const { displayCurrency } = useAppContext()
  return <HomePage displayCurrency={displayCurrency} />
}

function AssetWrapper() {
  const { displayCurrency } = useAppContext()
  return <AssetPage displayCurrency={displayCurrency} />
}

const theme = extendTheme({
  colorSchemes: {
    light: {
      palette: {
        primary: {
          50: '#eff6ff',
          100: '#dbeafe',
          200: '#bfdbfe',
          300: '#93c5fd',
          400: '#60a5fa',
          500: '#3b82f6',
          600: '#2563eb',
          700: '#1d4ed8',
          800: '#1e40af',
          900: '#1e3a8a',
        },
        background: {
          body: '#f8fafc',
          surface: '#ffffff',
        },
        neutral: {
          50: '#f8fafc',
          100: '#f1f5f9',
          200: '#e2e8f0',
          300: '#cbd5e1',
          400: '#94a3b8',
          500: '#64748b',
          600: '#475569',
          700: '#334155',
          800: '#1e293b',
          900: '#0f172a',
        },
      },
    },
  },
  fontFamily: {
    body: "'IBM Plex Sans', 'system-ui', sans-serif",
    display: "'IBM Plex Sans', 'system-ui', sans-serif",
  },
})

function ProtectedRoute() {
  const { user, loading } = useAuth()
  if (loading) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--joy-palette-background-body)' }}>
        <CircularProgress size="lg" />
      </Box>
    )
  }
  if (!user) return <Navigate to="/login" replace />
  return <Outlet />
}

function PublicRoute() {
  const { user, loading } = useAuth()
  if (loading) return null
  if (user) return <Navigate to="/" replace />
  return <Outlet />
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <CssVarsProvider theme={theme} defaultMode="light">
      <CssBaseline />
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route element={<PublicRoute />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/signup" element={<SignupPage />} />
            </Route>
            <Route element={<ProtectedRoute />}>
              <Route element={<App />}>
                <Route path="/" element={<HomeWrapper />} />
                <Route path="/asset/:assetId" element={<AssetWrapper />} />
                <Route path="/profile" element={<ProfilePage />} />
                <Route path="/admin" element={<AdminPage />} />
              </Route>
            </Route>
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </CssVarsProvider>
  </StrictMode>,
)
