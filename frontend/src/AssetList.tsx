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
      const res = await createAsset({ name: name.trim(), currency })
      const newAsset: AssetDto = { id: res.assetId, name: name.trim(), ownerId: '', currency, staleAfter: 0 }
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
        sx={{ borderRadius: '12px', p: 2 }}
      >
        <Typography level="title-sm" sx={{ mb: 2 }}>
          New Asset
        </Typography>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          <FormControl size="sm">
            <FormLabel sx={{ fontSize: '11px' }}>Name</FormLabel>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
              placeholder="e.g. Bitcoin holdings"
              size="sm"
            />
          </FormControl>
          <FormControl size="sm">
            <FormLabel sx={{ fontSize: '11px' }}>Currency</FormLabel>
            <Input
              value={currency}
              onChange={(e) => setCurrency(e.target.value.toUpperCase())}
              placeholder="USD"
              size="sm"
            />
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 0.75 }}>
              {POPULAR_CURRENCIES.map((c) => (
                <Chip
                  key={c}
                  size="sm"
                  variant={currency === c ? 'solid' : 'outlined'}
                  color={currency === c ? 'primary' : 'neutral'}
                  onClick={() => setCurrency(c)}
                  sx={{ cursor: 'pointer', fontSize: '10px' }}
                >
                  {c}
                </Chip>
              ))}
            </Box>
          </FormControl>
          {createError && (
            <Typography level="body-xs" color="danger">{createError}</Typography>
          )}
          <Button
            onClick={handleCreate}
            loading={creating}
            size="sm"
            variant="soft"
            color="primary"
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
          <Typography level="body-xs" sx={{ color: 'neutral.500', textAlign: 'center', pt: 2 }}>
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
                  bgcolor: selectedId === asset.id ? 'primary.50' : 'transparent',
                  border: '1px solid',
                  borderColor: selectedId === asset.id ? 'primary.200' : 'transparent',
                  '&:hover': {
                    bgcolor: selectedId === asset.id ? 'primary.100' : 'neutral.100',
                  },
                }}
              >
                <Box>
                  <Typography
                    level="body-sm"
                    sx={{
                      color: selectedId === asset.id ? 'primary.700' : 'neutral.800',
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
        <ModalDialog variant="outlined" sx={{ borderRadius: '16px', maxWidth: 400 }}>
          <DialogTitle>Delete Asset</DialogTitle>
          <DialogContent>
            <Typography level="body-sm" sx={{ color: 'neutral.600' }}>
              Are you sure you want to delete <strong>{deleteTarget?.name}</strong>?
              This will also delete all associated transactions and cannot be undone.
            </Typography>
          </DialogContent>
          <DialogActions>
            <Button variant="plain" onClick={() => setDeleteTarget(null)} disabled={deleting}>
              Cancel
            </Button>
            <Button color="danger" onClick={handleDelete} loading={deleting}>
              Delete
            </Button>
          </DialogActions>
        </ModalDialog>
      </Modal>
    </Box>
  )
}
