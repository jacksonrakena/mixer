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

function ProtectedRoute() {
  const { user, loading } = useAuth()
  if (loading) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#0a0e1a' }}>
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
    <CssVarsProvider theme={theme} defaultMode="dark">
      <CssBaseline />
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route element={<PublicRoute />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/signup" element={<SignupPage />} />
            </Route>
            <Route element={<ProtectedRoute />}>
              <Route path="/" element={<App />} />
              <Route path="/profile" element={<App page="profile" />} />
              <Route path="/admin" element={<App page="admin" />} />
            </Route>
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </CssVarsProvider>
  </StrictMode>,
)
