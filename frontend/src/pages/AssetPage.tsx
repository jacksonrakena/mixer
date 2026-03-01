import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import Box from '@mui/joy/Box'
import Typography from '@mui/joy/Typography'
import Sheet from '@mui/joy/Sheet'
import CircularProgress from '@mui/joy/CircularProgress'
import IconButton from '@mui/joy/IconButton'
import Dropdown from '@mui/joy/Dropdown'
import Menu from '@mui/joy/Menu'
import MenuButton from '@mui/joy/MenuButton'
import MenuItem from '@mui/joy/MenuItem'
import { type AssetDto, type SupportedCurrency } from '../api'
import { AssetChart } from '../AssetChart'
import { TransactionPanel } from '../TransactionPanel'
import { DeleteAssetModal, EditAssetModal } from '../AssetList'

interface AssetPageProps {
  displayCurrency: SupportedCurrency
  assets: AssetDto[]
  refreshAssets: () => Promise<AssetDto[]>
  enabledMarketSources: string[]
}

export default function AssetPage({ displayCurrency, assets: propAssets, refreshAssets, enabledMarketSources }: AssetPageProps) {
  const { assetId } = useParams<{ assetId: string }>()
  const navigate = useNavigate()
  const [asset, setAsset] = useState<AssetDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [staleAfter, setStaleAfter] = useState(0)
  const [aggregatedThrough, setAggregatedThrough] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<AssetDto | null>(null)
  const [editTarget, setEditTarget] = useState<AssetDto | null>(null)

  useEffect(() => {
    if (!assetId) return
    setLoading(true)
    const found = propAssets.find((a) => a.id === assetId) ?? null
    setAsset(found)
    if (found) {
      setStaleAfter(found.staleAfter)
      setAggregatedThrough(found.aggregatedThrough)
    }
    setLoading(false)
  }, [assetId, propAssets])

  const handleTransactionChange = useCallback((newStaleAfter: number) => {
    if (newStaleAfter > 0) setStaleAfter(newStaleAfter)
  }, [])

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <CircularProgress size="md" />
      </Box>
    )
  }

  if (!asset) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <Typography level="body-md" sx={{ color: 'neutral.500' }}>Asset not found.</Typography>
      </Box>
    )
  }

  return (
    <Box sx={{ maxWidth: 1100, mx: 'auto', width: '100%', display: 'flex', flexDirection: 'column', gap: 2.5 }}>
      {/* Chart */}
      <AssetChart
        key={`chart-${asset.id}`}
        assetId={asset.id}
        assetName={asset.name}
        currency={asset.currency}
        staleAfter={staleAfter}
        aggregatedThrough={aggregatedThrough}
        displayCurrency={displayCurrency}
        onStaleResolved={refreshAssets}
        headerAction={
          <Dropdown>
            <MenuButton slots={{ root: IconButton }} slotProps={{ root: { variant: 'outlined', color: 'neutral', size: 'sm' } }}>
              ⋯
            </MenuButton>
            <Menu placement="bottom-end" size="sm">
              <MenuItem onClick={() => setEditTarget(asset)}>Edit</MenuItem>
              <MenuItem color="danger" onClick={() => setDeleteTarget(asset)}>Delete</MenuItem>
            </Menu>
          </Dropdown>
        }
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
          Transactions — {asset.name}
        </Typography>
        <TransactionPanel
          assetId={asset.id}
          currency={asset.currency}
          onTransactionChange={handleTransactionChange}
        />
      </Sheet>

      {/* Edit modal */}
      <EditAssetModal
        asset={editTarget}
        onClose={() => setEditTarget(null)}
        enabledMarketSources={enabledMarketSources}
        onUpdated={() => {
          setEditTarget(null)
          refreshAssets()
        }}
      />

      {/* Delete modal */}
      <DeleteAssetModal
        asset={deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onDeleted={() => {
          setDeleteTarget(null)
          refreshAssets()
          navigate('/')
        }}
      />
    </Box>
  )
}
