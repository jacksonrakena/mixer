import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  Outlet,
} from "react-router-dom";
import { CssVarsProvider, extendTheme } from "@mui/joy/styles";
import CssBaseline from "@mui/joy/CssBaseline";
import Box from "@mui/joy/Box";
import CircularProgress from "@mui/joy/CircularProgress";
import "./index.css";
import App from "./App.tsx";
import { AuthProvider, useAuth } from "./AuthContext.tsx";
import LoginPage from "./pages/LoginPage.tsx";
import SignupPage from "./pages/SignupPage.tsx";
import ProfilePage from "./pages/ProfilePage.tsx";
import AdminPage from "./pages/AdminPage.tsx";
import HomePage from "./pages/HomePage.tsx";
import AssetPage from "./pages/AssetPage.tsx";
import { useOutletContext } from "react-router-dom";
import type { SupportedCurrency, AssetDto } from "./api";

interface AppContext {
  displayCurrency: SupportedCurrency;
  assets: AssetDto[];
  loadingAssets: boolean;
  refreshAssets: () => Promise<AssetDto[]>;
  enabledMarketSources: string[];
}

export function useAppContext() {
  return useOutletContext<AppContext>();
}

function HomeWrapper() {
  const { displayCurrency, assets, refreshAssets } = useAppContext();
  return <HomePage displayCurrency={displayCurrency} assets={assets} refreshAssets={refreshAssets} />;
}

function AssetWrapper() {
  const { displayCurrency, assets, refreshAssets, enabledMarketSources } = useAppContext();
  return <AssetPage displayCurrency={displayCurrency} assets={assets} refreshAssets={refreshAssets} enabledMarketSources={enabledMarketSources} />;
}

const theme = extendTheme({
  colorSchemes: {
    light: {
      palette: {
        primary: {
          "50": "#e0f2f1",
          "100": "#b2dfdb",
          "200": "#80cbc4",
          "300": "#4db6ac",
          "400": "#26a69a",
          "500": "#009688",
          "600": "#00897b",
          "700": "#00796b",
          "800": "#00695c",
          "900": "#004d40",
        },
        background: {
          body: "#f8fafc",
          surface: "#ffffff",
        },
        neutral: {
          50: "#f8fafc",
          100: "#f1f5f9",
          200: "#e2e8f0",
          300: "#cbd5e1",
          400: "#94a3b8",
          500: "#64748b",
          600: "#475569",
          700: "#334155",
          800: "#1e293b",
          900: "#0f172a",
        },
      },
    },
  },
  fontFamily: {
    body: "'IBM Plex Sans', 'system-ui', sans-serif",
    display: "'IBM Plex Sans', 'system-ui', sans-serif",
  },
});

function ProtectedRoute() {
  const { user, loading } = useAuth();
  if (loading) {
    return (
      <Box
        sx={{
          minHeight: "100vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "var(--joy-palette-background-body)",
        }}
      >
        <CircularProgress size="lg" />
      </Box>
    );
  }
  if (!user) return <Navigate to="/login" replace />;
  return <Outlet />;
}

function AdminRoute() {
  const { user } = useAuth();
  if (!user?.roles.includes("GLOBAL_ADMIN")) return <Navigate to="/" replace />;
  return <Outlet />;
}

function PublicRoute() {
  const { user, loading } = useAuth();
  if (loading) return null;
  if (user) return <Navigate to="/" replace />;
  return <Outlet />;
}

createRoot(document.getElementById("root")!).render(
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
                <Route element={<AdminRoute />}>
                  <Route path="/admin" element={<AdminPage />} />
                </Route>
              </Route>
            </Route>
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </CssVarsProvider>
  </StrictMode>,
);
