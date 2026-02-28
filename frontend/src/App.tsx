import { useEffect, useState } from 'react'
import { useNavigate, useLocation, Outlet } from 'react-router-dom'
import Box from '@mui/joy/Box'
import Typography from '@mui/joy/Typography'
import Divider from '@mui/joy/Divider'
import Select from '@mui/joy/Select'
import Option from '@mui/joy/Option'
import IconButton from '@mui/joy/IconButton'
import Menu from '@mui/joy/Menu'
import MenuItem from '@mui/joy/MenuItem'
import MenuButton from '@mui/joy/MenuButton'
import Dropdown from '@mui/joy/Dropdown'
import CircularProgress from '@mui/joy/CircularProgress'
import { fetchAssets, fetchConfig, type AssetDto, type SupportedCurrency } from './api'
import { CreateAssetModal } from './AssetList'
import { useAuth } from './AuthContext'

const CURRENCY_NAMES: Record<string, string> = {
  AUD: 'Australian Dollar',
  USD: 'US Dollar',
  NZD: 'New Zealand Dollar',
  EUR: 'Euro',
  GBP: 'British Pound',
  HKD: 'Hong Kong Dollar',
}

const SIDEBAR_WIDTH = 260
const SIDEBAR_COLLAPSED_WIDTH = 56

export default function App() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [assets, setAssets] = useState<AssetDto[]>([])
  const [loadingAssets, setLoadingAssets] = useState(true)
  const [displayCurrency, setDisplayCurrency] = useState<SupportedCurrency>(
    (user?.displayCurrency as SupportedCurrency) ?? 'AUD',
  )
  const [collapsed, setCollapsed] = useState(false)
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [currencies, setCurrencies] = useState<string[]>([])

  useEffect(() => {
    fetchAssets()
      .then(setAssets)
      .catch(console.error)
      .finally(() => setLoadingAssets(false))
    fetchConfig()
      .then((cfg) => setCurrencies(cfg.currencies))
      .catch(console.error)
  }, [])

  const currentAssetId = location.pathname.startsWith('/asset/') ? location.pathname.split('/')[2] : null

  const sidebarWidth = collapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_WIDTH

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.body', display: 'flex' }}>
      {/* Sidebar */}
      <Box
        component="nav"
        sx={{
          width: sidebarWidth,
          flexShrink: 0,
          height: '100vh',
          position: 'sticky',
          top: 0,
          display: 'flex',
          flexDirection: 'column',
          borderRight: '1px solid',
          borderColor: 'neutral.200',
          bgcolor: 'background.surface',
          transition: 'width 0.2s ease',
          overflow: 'hidden',
        }}
      >
        {/* Brand */}
        <Box
          sx={{
            px: collapsed ? 0 : 2.5,
            py: 2,
            display: 'flex',
            alignItems: 'center',
            gap: 1.5,
            justifyContent: collapsed ? 'center' : 'flex-start',
            cursor: 'pointer',
          }}
          onClick={() => navigate('/')}
        >
          <Box
            sx={{
              width: 32,
              height: 32,
              borderRadius: '8px',
              background: 'linear-gradient(135deg, var(--joy-palette-primary-400), var(--joy-palette-primary-700))',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '14px',
              fontWeight: 800,
              color: 'white',
              flexShrink: 0,
            }}
          >
            M
          </Box>
          {!collapsed && (
            <Typography level="title-md" sx={{ fontWeight: 700, letterSpacing: '-0.3px' }}>
              Mixer
            </Typography>
          )}
        </Box>

        <Divider />

        {/* Navigation section */}
        <Box sx={{ flex: 1, overflow: 'auto', py: 1 }}>
          {/* Dashboard link */}
          <SidebarItem
            label="Dashboard"
            icon="📊"
            active={location.pathname === '/'}
            collapsed={collapsed}
            onClick={() => navigate('/')}
          />

          {/* Assets section */}
          {!collapsed && (
            <Typography
              level="body-xs"
              sx={{
                color: 'neutral.500',
                textTransform: 'uppercase',
                letterSpacing: '0.08em',
                fontWeight: 600,
                px: 2.5,
                pt: 2,
                pb: 0.75,
              }}
            >
              Assets
            </Typography>
          )}

          {collapsed && <Divider sx={{ my: 0.5 }} />}

          {loadingAssets ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
              <CircularProgress size="sm" />
            </Box>
          ) : (
            assets.map((asset) => (
              <SidebarItem
                key={asset.id}
                label={asset.name}
                sublabel={!collapsed ? asset.currency : undefined}
                active={currentAssetId === asset.id}
                collapsed={collapsed}
                onClick={() => navigate(`/asset/${asset.id}`)}
              />
            ))
          )}

          {/* Create asset button */}
          <SidebarItem
            label="Create asset"
            icon="＋"
            collapsed={collapsed}
            onClick={() => setCreateModalOpen(true)}
            muted
          />

          {!collapsed && (
            <>
              <Divider sx={{ my: 1 }} />

              {/* Display currency selector */}
              <Typography
                level="body-xs"
                sx={{
                  color: 'neutral.500',
                  textTransform: 'uppercase',
                  letterSpacing: '0.08em',
                  fontWeight: 600,
                  px: 2.5,
                  pt: 1,
                  pb: 0.75,
                }}
              >
                Display Currency
              </Typography>
              <Box sx={{ px: 2 }}>
                <Select
                  value={displayCurrency}
                  onChange={(_, val) => val && setDisplayCurrency(val as SupportedCurrency)}
                  size="sm"
                  renderValue={(selected) => selected ? (
                    <Box sx={{ textAlign: 'left' }}>
                      <Typography level="body-sm" sx={{ fontWeight: 600, lineHeight: 1.3 }}>
                        {selected.value}
                      </Typography>
                      <Typography level="body-xs" sx={{ color: 'neutral.500', lineHeight: 1.2 }}>
                        {CURRENCY_NAMES[selected.value as string] ?? ''}
                      </Typography>
                    </Box>
                  ) : null}
                  sx={{
                    fontWeight: 600,
                    fontSize: '13px',
                    borderRadius: '8px',
                    py: 0.75,
                    textAlign: 'left',
                    '& .MuiSelect-button': { textAlign: 'left' },
                  }}
                >
                  {currencies.map((cur) => (
                    <Option key={cur} value={cur}>
                      <Box>
                        <Typography level="body-sm" sx={{ fontWeight: 600 }}>{cur}</Typography>
                        <Typography level="body-xs" sx={{ color: 'neutral.500' }}>
                          {CURRENCY_NAMES[cur] ?? cur}
                        </Typography>
                      </Box>
                    </Option>
                  ))}
                </Select>
              </Box>
            </>
          )}
        </Box>

        <Divider />

        {/* Bottom section — user + collapse toggle */}
        <Box sx={{ p: collapsed ? 0.5 : 1.5 }}>
          {!collapsed && (
            <Dropdown>
              <MenuButton
                variant="plain"
                sx={{
                  width: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1.5,
                  px: 1.5,
                  py: 1,
                  borderRadius: '10px',
                  justifyContent: 'flex-start',
                  textAlign: 'left',
                  '&:hover': { bgcolor: 'neutral.100' },
                }}
              >
                <Box
                  sx={{
                    width: 32,
                    height: 32,
                    borderRadius: '50%',
                    bgcolor: 'primary.100',
                    color: 'primary.700',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '13px',
                    fontWeight: 700,
                    flexShrink: 0,
                  }}
                >
                  {(user?.displayName ?? user?.email ?? '?')[0].toUpperCase()}
                </Box>
                <Box sx={{ minWidth: 0 }}>
                  <Typography level="body-sm" sx={{ fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {user?.displayName ?? user?.email ?? ''}
                  </Typography>
                  <Typography level="body-xs" sx={{ color: 'neutral.500', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {user?.email ?? ''}
                  </Typography>
                </Box>
              </MenuButton>
              <Menu
                placement="top-start"
                sx={{ minWidth: 180, borderRadius: '10px' }}
              >
                <MenuItem onClick={() => navigate('/profile')}>
                  👤&ensp;Profile
                </MenuItem>
                {user?.roles.includes('GLOBAL_ADMIN') && (
                  <MenuItem onClick={() => navigate('/admin')}>
                    ⚙️&ensp;Admin
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
                  🚪&ensp;Sign out
                </MenuItem>
              </Menu>
            </Dropdown>
          )}

          <IconButton
            variant="plain"
            size="sm"
            onClick={() => setCollapsed(!collapsed)}
            sx={{
              mt: collapsed ? 0 : 0.5,
              mx: collapsed ? 'auto' : 0,
              display: 'flex',
              width: collapsed ? undefined : '100%',
              borderRadius: '8px',
              color: 'neutral.500',
              fontSize: '16px',
            }}
            title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? '»' : '«'}
          </IconButton>
        </Box>
      </Box>

      {/* Main content */}
      <Box
        sx={{
          flex: 1,
          minWidth: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: 2.5,
          p: { xs: 1.5, md: 3 },
          maxWidth: 1200,
          mx: 'auto',
          width: '100%',
        }}
      >
        <Outlet context={{ displayCurrency }} />
      </Box>

      {/* Create asset modal */}
      <CreateAssetModal
        open={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        onCreated={(asset) => {
          setAssets((prev) => [...prev, asset])
          setCreateModalOpen(false)
          navigate(`/asset/${asset.id}`)
        }}
      />
    </Box>
  )
}

/** A single row in the sidebar navigation. */
function SidebarItem({
  label,
  sublabel,
  icon,
  active,
  collapsed,
  muted,
  onClick,
}: {
  label: string
  sublabel?: string
  icon?: string
  active?: boolean
  collapsed: boolean
  muted?: boolean
  onClick: () => void
}) {
  return (
    <Box
      onClick={onClick}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        px: collapsed ? 0 : 2.5,
        py: 0.75,
        mx: collapsed ? 0.5 : 1,
        borderRadius: '8px',
        cursor: 'pointer',
        justifyContent: collapsed ? 'center' : 'flex-start',
        bgcolor: active ? 'primary.50' : 'transparent',
        color: active ? 'primary.700' : muted ? 'neutral.500' : 'neutral.800',
        fontWeight: active ? 600 : 400,
        transition: 'all 0.12s',
        '&:hover': {
          bgcolor: active ? 'primary.100' : 'neutral.100',
        },
      }}
      title={collapsed ? label : undefined}
    >
      {icon && (
        <Box sx={{ width: 20, textAlign: 'center', flexShrink: 0, fontSize: '14px' }}>
          {icon}
        </Box>
      )}
      {!collapsed && (
        <Box sx={{ minWidth: 0, flex: 1 }}>
          <Typography
            level="body-sm"
            sx={{
              fontWeight: 'inherit',
              color: 'inherit',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {label}
          </Typography>
          {sublabel && (
            <Typography level="body-xs" sx={{ color: 'neutral.500' }}>
              {sublabel}
            </Typography>
          )}
        </Box>
      )}
    </Box>
  )
}

