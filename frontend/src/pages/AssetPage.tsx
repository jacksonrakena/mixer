import { useState, useEffect, useCallback } from 'react'
import { useParams } from 'react-router-dom'
import Box from '@mui/joy/Box'
import Typography from '@mui/joy/Typography'
import Sheet from '@mui/joy/Sheet'
import CircularProgress from '@mui/joy/CircularProgress'
import { type AssetDto, type SupportedCurrency } from '../api'
import { AssetChart } from '../AssetChart'
import { TransactionPanel } from '../TransactionPanel'

interface AssetPageProps {
  displayCurrency: SupportedCurrency
  assets: AssetDto[]
}

export default function AssetPage({ displayCurrency, assets: propAssets }: AssetPageProps) {
  const { assetId } = useParams<{ assetId: string }>()
  const [asset, setAsset] = useState<AssetDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [staleAfter, setStaleAfter] = useState(0)
  const [aggregatedThrough, setAggregatedThrough] = useState<string | null>(null)

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
    </Box>
  )
}
