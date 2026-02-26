import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import Box from '@mui/joy/Box'
import Typography from '@mui/joy/Typography'
import Sheet from '@mui/joy/Sheet'
import Divider from '@mui/joy/Divider'
import Select from '@mui/joy/Select'
import Option from '@mui/joy/Option'
import IconButton from '@mui/joy/IconButton'
import Menu from '@mui/joy/Menu'
import MenuItem from '@mui/joy/MenuItem'
import MenuButton from '@mui/joy/MenuButton'
import Dropdown from '@mui/joy/Dropdown'
import ListItemDecorator from '@mui/joy/ListItemDecorator'
import { fetchAssets, SUPPORTED_CURRENCIES, type AssetDto, type SupportedCurrency } from './api'
import { AssetList } from './AssetList'
import { AssetChart } from './AssetChart'
import { TransactionPanel } from './TransactionPanel'
import { useAuth } from './AuthContext'
import ProfilePage from './pages/ProfilePage'
import AdminPage from './pages/AdminPage'

interface AppProps {
  page?: 'profile' | 'admin'
}

export default function App({ page }: AppProps) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [assets, setAssets] = useState<AssetDto[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [loadingAssets, setLoadingAssets] = useState(true)
  const [staleAfterMap, setStaleAfterMap] = useState<Record<string, number>>({})
  const [displayCurrency, setDisplayCurrency] = useState<SupportedCurrency>(
    (user?.displayCurrency as SupportedCurrency) ?? 'AUD',
  )

  useEffect(() => {
    fetchAssets()
      .then((data) => {
        setAssets(data)
        if (data.length > 0) setSelectedId(data[0].id)
        // Initialize staleAfter from asset data
        const initialStale: Record<string, number> = {}
        for (const asset of data) {
          if (asset.staleAfter > 0) initialStale[asset.id] = asset.staleAfter
        }
        setStaleAfterMap(initialStale)
      })
      .catch(console.error)
      .finally(() => setLoadingAssets(false))
  }, [])

  const selectedAsset = assets.find((a) => a.id === selectedId) ?? null

  // Called after a transaction is added/removed — update staleness for the asset
  const handleTransactionChange = useCallback((staleAfter: number) => {
    if (selectedId && staleAfter > 0) {
      setStaleAfterMap((prev) => ({ ...prev, [selectedId]: staleAfter }))
    }
  }, [selectedId])

  return (
    <Box
      sx={{
        minHeight: '100vh',
        bgcolor: 'background.body',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      {/* Top nav */}
      <Sheet
        component="header"
        variant="outlined"
        sx={{
          borderTop: 'none',
          borderLeft: 'none',
          borderRight: 'none',
          position: 'sticky',
          top: 0,
          zIndex: 100,
          px: { xs: 2, md: 4 },
          py: 1.5,
          display: 'flex',
          alignItems: 'center',
          gap: 2,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 30,
              height: 30,
              borderRadius: '8px',
              background: 'linear-gradient(135deg, #3b82f6, #2563eb)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '14px',
              fontWeight: 800,
              color: 'white',
              boxShadow: '0 0 12px rgba(37,99,235,0.3)',
            }}
          >
            M
          </Box>
          <Typography
            level="title-md"
            sx={{ fontWeight: 700, letterSpacing: '-0.3px' }}
          >
            Mixer
          </Typography>
        </Box>
        <Divider orientation="vertical" sx={{ mx: 0.5 }} />
        <Typography level="body-xs" sx={{ color: 'neutral.500' }}>
          Portfolio Tracker
        </Typography>

        {/* User menu */}
        <Box sx={{ ml: 'auto' }}>
          <Dropdown>
            <MenuButton
              variant="plain"
              sx={{
                color: 'neutral.700',
                fontWeight: 600,
                fontSize: '13px',
                borderRadius: '8px',
                px: 1.5,
              }}
            >
              {user?.displayName ?? user?.email ?? ''}
            </MenuButton>
            <Menu
              placement="bottom-end"
              sx={{ minWidth: 160, borderRadius: '10px' }}
            >
              <MenuItem onClick={() => navigate('/')}>
                <ListItemDecorator>📊</ListItemDecorator>
                Dashboard
              </MenuItem>
              <MenuItem onClick={() => navigate('/profile')}>
                <ListItemDecorator>👤</ListItemDecorator>
                Profile
              </MenuItem>
              {user?.roles.includes('GLOBAL_ADMIN') && (
                <MenuItem onClick={() => navigate('/admin')}>
                  <ListItemDecorator>⚙️</ListItemDecorator>
                  Admin
                </MenuItem>
              )}
              <Divider />
              <MenuItem
                onClick={async () => {
                  await logout()
                  navigate('/login', { replace: true })
                }}
                color="danger"
              >
                <ListItemDecorator>🚪</ListItemDecorator>
                Sign out
              </MenuItem>
            </Menu>
          </Dropdown>
        </Box>
      </Sheet>

      {/* Main layout */}
      {page === 'profile' ? (
        <ProfilePage />
      ) : page === 'admin' ? (
        <AdminPage />
      ) : (
      <Box
        sx={{
          flex: 1,
          display: 'flex',
          maxWidth: 1400,
          width: '100%',
          mx: 'auto',
          p: { xs: 1.5, md: 3 },
          gap: 2.5,
          alignItems: 'flex-start',
        }}
      >
        {/* Left sidebar — Asset list */}
        <Sheet
          variant="outlined"
          sx={{
            width: 260,
            flexShrink: 0,
            borderRadius: '16px',
            p: 2,
            position: 'sticky',
            top: 72,
            maxHeight: 'calc(100vh - 100px)',
            overflow: 'auto',
          }}
        >
          {/* Currency selector */}
          <Typography
            level="body-xs"
            sx={{ color: 'neutral.500', textTransform: 'uppercase', letterSpacing: '0.08em', mb: 0.75, fontWeight: 600 }}
          >
            Display Currency
          </Typography>
          <Select
            value={displayCurrency}
            onChange={(_, val) => val && setDisplayCurrency(val as SupportedCurrency)}
            size="sm"
            sx={{
              mb: 2,
              fontWeight: 600,
              fontSize: '13px',
              borderRadius: '10px',
            }}
          >
            {SUPPORTED_CURRENCIES.map((cur) => (
              <Option key={cur} value={cur}>
                {cur}
              </Option>
            ))}
          </Select>

          <Divider sx={{ mb: 1.5 }} />

          <Typography
            level="body-xs"
            sx={{ color: 'neutral.500', textTransform: 'uppercase', letterSpacing: '0.08em', mb: 1.5, fontWeight: 600 }}
          >
            Assets
          </Typography>
          <AssetList
            assets={assets}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onAssetsChange={setAssets}
            loading={loadingAssets}
          />
        </Sheet>

        {/* Main content */}
        <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2.5 }}>
          {selectedAsset ? (
            <>
              {/* Chart */}
              <AssetChart
                key={`chart-${selectedAsset.id}`}
                assetId={selectedAsset.id}
                assetName={selectedAsset.name}
                currency={selectedAsset.currency}
                staleAfter={staleAfterMap[selectedAsset.id] ?? 0}
                displayCurrency={displayCurrency}
              />

              {/* Transactions panel */}
              <Sheet
                variant="outlined"
                sx={{
                  borderRadius: '16px',
                  p: 2.5,
                  overflow: 'hidden',
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Typography
                  level="body-xs"
                  sx={{
                    color: 'neutral.500',
                    textTransform: 'uppercase',
                    letterSpacing: '0.08em',
                    mb: 2,
                    fontWeight: 600,
                  }}
                >
                  Transactions — {selectedAsset.name}
                </Typography>
                <TransactionPanel
                  assetId={selectedAsset.id}
                  onTransactionChange={handleTransactionChange}
                />
              </Sheet>
            </>
          ) : (
            /* Empty state */
            <Box
              sx={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                minHeight: 400,
                gap: 2,
              }}
            >
              <Box
                sx={{
                  width: 64,
                  height: 64,
                  borderRadius: '20px',
                  bgcolor: 'primary.50',
                  border: '1px solid',
                  borderColor: 'primary.200',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '28px',
                }}
              >
                📈
              </Box>
              <Typography level="title-md" sx={{ color: 'neutral.700' }}>
                No asset selected
              </Typography>
              <Typography level="body-sm" sx={{ color: 'neutral.500', textAlign: 'center', maxWidth: 300 }}>
                {loadingAssets
                  ? 'Loading your assets…'
                  : 'Create an asset in the sidebar to get started.'}
              </Typography>
            </Box>
          )}
        </Box>
      </Box>
      )}
    </Box>
  )
}
