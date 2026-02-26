import { useEffect, useState } from 'react'
import { useNavigate, Outlet } from 'react-router-dom'
import Box from '@mui/joy/Box'
import Typography from '@mui/joy/Typography'
import Sheet from '@mui/joy/Sheet'
import Divider from '@mui/joy/Divider'
import Select from '@mui/joy/Select'
import Option from '@mui/joy/Option'
import Menu from '@mui/joy/Menu'
import MenuItem from '@mui/joy/MenuItem'
import MenuButton from '@mui/joy/MenuButton'
import Dropdown from '@mui/joy/Dropdown'
import ListItemDecorator from '@mui/joy/ListItemDecorator'
import { fetchAssets, SUPPORTED_CURRENCIES, type AssetDto, type SupportedCurrency } from './api'
import { AssetList } from './AssetList'
import { useAuth } from './AuthContext'

export default function App() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [assets, setAssets] = useState<AssetDto[]>([])
  const [loadingAssets, setLoadingAssets] = useState(true)
  const [displayCurrency, setDisplayCurrency] = useState<SupportedCurrency>(
    (user?.displayCurrency as SupportedCurrency) ?? 'AUD',
  )

  useEffect(() => {
    fetchAssets()
      .then(setAssets)
      .catch(console.error)
      .finally(() => setLoadingAssets(false))
  }, [])

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
        <Box
          sx={{ display: 'flex', alignItems: 'center', gap: 1.5, cursor: 'pointer' }}
          onClick={() => navigate('/')}
        >
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
            selectedId={null}
            onSelect={(id) => navigate(`/asset/${id}`)}
            onAssetsChange={setAssets}
            loading={loadingAssets}
          />
        </Sheet>

        {/* Main content — rendered by child route */}
        <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2.5 }}>
          <Outlet context={{ displayCurrency }} />
        </Box>
      </Box>
    </Box>
  )
}

