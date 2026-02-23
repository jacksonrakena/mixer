import { useState } from 'react'
import Box from '@mui/joy/Box'
import Typography from '@mui/joy/Typography'
import Button from '@mui/joy/Button'
import IconButton from '@mui/joy/IconButton'
import Input from '@mui/joy/Input'
import FormControl from '@mui/joy/FormControl'
import FormLabel from '@mui/joy/FormLabel'
import Sheet from '@mui/joy/Sheet'
import Chip from '@mui/joy/Chip'
import CircularProgress from '@mui/joy/CircularProgress'
import Modal from '@mui/joy/Modal'
import ModalDialog from '@mui/joy/ModalDialog'
import DialogTitle from '@mui/joy/DialogTitle'
import DialogContent from '@mui/joy/DialogContent'
import DialogActions from '@mui/joy/DialogActions'
import { createAsset, deleteAsset, type AssetDto } from './api'

// Hard-coded owner ID — in a real app this would come from auth
const OWNER_ID = '00000000-0000-0000-0000-000000000001'

const POPULAR_CURRENCIES = ['USD', 'NZD', 'AUD', 'EUR', 'GBP', 'BTC', 'ETH']

interface AssetListProps {
  assets: AssetDto[]
  selectedId: string | null
  onSelect: (id: string) => void
  onAssetsChange: (assets: AssetDto[]) => void
  loading: boolean
}

export const AssetList = ({
  assets,
  selectedId,
  onSelect,
  onAssetsChange,
  loading,
}: AssetListProps) => {
  const [name, setName] = useState('')
  const [currency, setCurrency] = useState('USD')
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<AssetDto | null>(null)
  const [deleting, setDeleting] = useState(false)

  const handleCreate = async () => {
    if (!name.trim()) {
      setCreateError('Asset name is required.')
      return
    }
    setCreating(true)
    setCreateError(null)
    try {
      const res = await createAsset({ name: name.trim(), ownerId: OWNER_ID, currency })
      const newAsset: AssetDto = { id: res.assetId, name: name.trim(), ownerId: OWNER_ID, currency }
      onAssetsChange([...assets, newAsset])
      setName('')
      onSelect(res.assetId)
    } catch (e) {
      setCreateError(e instanceof Error ? e.message : 'Failed to create asset')
    } finally {
      setCreating(false)
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    setDeleting(true)
    try {
      await deleteAsset(deleteTarget.id)
      const updated = assets.filter((a) => a.id !== deleteTarget.id)
      onAssetsChange(updated)
      if (selectedId === deleteTarget.id) {
        onSelect(updated.length > 0 ? updated[0].id : '')
      }
      setDeleteTarget(null)
    } catch (_e) {
      // keep modal open on error
    } finally {
      setDeleting(false)
    }
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, height: '100%' }}>
      {/* Create form */}
      <Sheet
        variant="outlined"
        sx={{
          borderRadius: '12px',
          p: 2,
          background: 'rgba(255,255,255,0.03)',
          border: '1px solid rgba(255,255,255,0.07)',
        }}
      >
        <Typography level="title-sm" sx={{ mb: 2, color: 'neutral.300' }}>
          New Asset
        </Typography>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          <FormControl size="sm">
            <FormLabel sx={{ color: 'neutral.400', fontSize: '11px' }}>Name</FormLabel>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
              placeholder="e.g. Bitcoin holdings"
              size="sm"
              sx={{
                background: 'rgba(255,255,255,0.05)',
                border: '1px solid rgba(255,255,255,0.1)',
                color: 'white',
                '& input::placeholder': { color: 'neutral.600' },
              }}
            />
          </FormControl>
          <FormControl size="sm">
            <FormLabel sx={{ color: 'neutral.400', fontSize: '11px' }}>Currency</FormLabel>
            <Input
              value={currency}
              onChange={(e) => setCurrency(e.target.value.toUpperCase())}
              placeholder="USD"
              size="sm"
              sx={{
                background: 'rgba(255,255,255,0.05)',
                border: '1px solid rgba(255,255,255,0.1)',
                color: 'white',
                '& input::placeholder': { color: 'neutral.600' },
              }}
            />
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 0.75 }}>
              {POPULAR_CURRENCIES.map((c) => (
                <Chip
                  key={c}
                  size="sm"
                  onClick={() => setCurrency(c)}
                  sx={{
                    cursor: 'pointer',
                    fontSize: '10px',
                    background: currency === c ? 'rgba(99,102,241,0.25)' : 'rgba(255,255,255,0.05)',
                    color: currency === c ? '#818cf8' : 'neutral.400',
                    border: currency === c ? '1px solid rgba(99,102,241,0.4)' : '1px solid rgba(255,255,255,0.07)',
                    '&:hover': { background: 'rgba(99,102,241,0.15)' },
                  }}
                >
                  {c}
                </Chip>
              ))}
            </Box>
          </FormControl>
          {createError && (
            <Typography level="body-xs" sx={{ color: 'danger.400' }}>{createError}</Typography>
          )}
          <Button
            onClick={handleCreate}
            loading={creating}
            size="sm"
            sx={{
              background: 'rgba(99,102,241,0.2)',
              color: '#818cf8',
              border: '1px solid rgba(99,102,241,0.3)',
              '&:hover': { background: 'rgba(99,102,241,0.35)' },
            }}
          >
            Create Asset
          </Button>
        </Box>
      </Sheet>

      {/* Asset list */}
      <Box sx={{ flex: 1, overflow: 'auto' }}>
        <Typography level="body-xs" sx={{ color: 'neutral.500', mb: 1, px: 0.5 }}>
          {loading ? 'Loading…' : `${assets.length} asset${assets.length !== 1 ? 's' : ''}`}
        </Typography>
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', pt: 3 }}>
            <CircularProgress size="sm" />
          </Box>
        ) : assets.length === 0 ? (
          <Typography level="body-xs" sx={{ color: 'neutral.600', textAlign: 'center', pt: 2 }}>
            No assets yet. Create one above.
          </Typography>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
            {assets.map((asset) => (
              <Box
                key={asset.id}
                onClick={() => onSelect(asset.id)}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  px: 1.5,
                  py: 1,
                  borderRadius: '10px',
                  cursor: 'pointer',
                  transition: 'all 0.15s',
                  background: selectedId === asset.id
                    ? 'rgba(99,102,241,0.18)'
                    : 'transparent',
                  border: selectedId === asset.id
                    ? '1px solid rgba(99,102,241,0.35)'
                    : '1px solid transparent',
                  '&:hover': {
                    background: selectedId === asset.id
                      ? 'rgba(99,102,241,0.22)'
                      : 'rgba(255,255,255,0.04)',
                  },
                }}
              >
                <Box>
                  <Typography
                    level="body-sm"
                    sx={{
                      color: selectedId === asset.id ? '#a5b4fc' : 'neutral.200',
                      fontWeight: selectedId === asset.id ? 600 : 400,
                    }}
                  >
                    {asset.name}
                  </Typography>
                  <Typography level="body-xs" sx={{ color: 'neutral.500' }}>
                    {asset.currency}
                  </Typography>
                </Box>
                <IconButton
                  size="sm"
                  variant="plain"
                  color="danger"
                  onClick={(e) => { e.stopPropagation(); setDeleteTarget(asset) }}
                  sx={{
                    minWidth: 24,
                    minHeight: 24,
                    opacity: 0,
                    transition: 'opacity 0.15s',
                    '.asset-row:hover &': { opacity: 1 },
                    '&:hover': { opacity: '1 !important' },
                  }}
                  className="delete-btn"
                >
                  <span style={{ fontSize: 12 }}>✕</span>
                </IconButton>
              </Box>
            ))}
          </Box>
        )}
      </Box>

      {/* Delete confirmation modal */}
      <Modal open={!!deleteTarget} onClose={() => !deleting && setDeleteTarget(null)}>
        <ModalDialog
          variant="outlined"
          sx={{
            background: '#111827',
            border: '1px solid rgba(255,255,255,0.1)',
            borderRadius: '16px',
            maxWidth: 400,
          }}
        >
          <DialogTitle sx={{ color: 'white' }}>Delete Asset</DialogTitle>
          <DialogContent>
            <Typography level="body-sm" sx={{ color: 'neutral.400' }}>
              Are you sure you want to delete <strong style={{ color: 'white' }}>{deleteTarget?.name}</strong>?
              This will also delete all associated transactions and cannot be undone.
            </Typography>
          </DialogContent>
          <DialogActions>
            <Button
              variant="plain"
              onClick={() => setDeleteTarget(null)}
              disabled={deleting}
              sx={{ color: 'neutral.400' }}
            >
              Cancel
            </Button>
            <Button
              color="danger"
              onClick={handleDelete}
              loading={deleting}
              sx={{ background: 'rgba(239,68,68,0.2)', color: '#f87171', border: '1px solid rgba(239,68,68,0.3)' }}
            >
              Delete
            </Button>
          </DialogActions>
        </ModalDialog>
      </Modal>
    </Box>
  )
}
