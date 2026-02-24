import { useEffect, useState, useCallback } from 'react'
import Box from '@mui/joy/Box'
import Typography from '@mui/joy/Typography'
import Sheet from '@mui/joy/Sheet'
import Divider from '@mui/joy/Divider'
import { fetchAssets, type AssetDto } from './api'
import { AssetList } from './AssetList'
import { AssetChart } from './AssetChart'
import { TransactionPanel } from './TransactionPanel'

export default function App() {
  const [assets, setAssets] = useState<AssetDto[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [loadingAssets, setLoadingAssets] = useState(true)
  const [staleAfterMap, setStaleAfterMap] = useState<Record<string, number>>({})

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
        background: 'linear-gradient(135deg, #0a0e1a 0%, #0d1224 50%, #0a0e1a 100%)',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      {/* Top nav */}
      <Sheet
        component="header"
        sx={{
          background: 'rgba(17,24,39,0.8)',
          borderBottom: '1px solid rgba(255,255,255,0.06)',
          backdropFilter: 'blur(12px)',
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
          {/* Logo mark */}
          <Box
            sx={{
              width: 30,
              height: 30,
              borderRadius: '8px',
              background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '14px',
              fontWeight: 800,
              color: 'white',
              boxShadow: '0 0 16px rgba(99,102,241,0.4)',
            }}
          >
            M
          </Box>
          <Typography
            level="title-md"
            sx={{ color: 'white', fontWeight: 700, letterSpacing: '-0.3px' }}
          >
            Mixer
          </Typography>
        </Box>
        <Divider orientation="vertical" sx={{ borderColor: 'rgba(255,255,255,0.08)', mx: 0.5 }} />
        <Typography level="body-xs" sx={{ color: 'neutral.500' }}>
          Portfolio Tracker
        </Typography>
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
          sx={{
            width: 260,
            flexShrink: 0,
            borderRadius: '16px',
            background: 'rgba(17,24,39,0.6)',
            border: '1px solid rgba(255,255,255,0.07)',
            backdropFilter: 'blur(12px)',
            p: 2,
            position: 'sticky',
            top: 72,
            maxHeight: 'calc(100vh - 100px)',
            overflow: 'auto',
          }}
        >
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
              />

              {/* Transactions panel */}
              <Sheet
                sx={{
                  borderRadius: '16px',
                  background: 'rgba(17,24,39,0.6)',
                  border: '1px solid rgba(255,255,255,0.07)',
                  backdropFilter: 'blur(12px)',
                  p: 2.5,
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
                  background: 'rgba(99,102,241,0.12)',
                  border: '1px solid rgba(99,102,241,0.2)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '28px',
                }}
              >
                📈
              </Box>
              <Typography level="title-md" sx={{ color: 'neutral.300' }}>
                No asset selected
              </Typography>
              <Typography level="body-sm" sx={{ color: 'neutral.600', textAlign: 'center', maxWidth: 300 }}>
                {loadingAssets
                  ? 'Loading your assets…'
                  : 'Create an asset in the sidebar to get started.'}
              </Typography>
            </Box>
          )}
        </Box>
      </Box>
    </Box>
  )
}
