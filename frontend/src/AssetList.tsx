import { useState, useEffect } from 'react'
import Box from '@mui/joy/Box'
import Typography from '@mui/joy/Typography'
import Button from '@mui/joy/Button'
import Input from '@mui/joy/Input'
import FormControl from '@mui/joy/FormControl'
import FormLabel from '@mui/joy/FormLabel'
import Chip from '@mui/joy/Chip'
import Modal from '@mui/joy/Modal'
import ModalDialog from '@mui/joy/ModalDialog'
import DialogTitle from '@mui/joy/DialogTitle'
import DialogContent from '@mui/joy/DialogContent'
import DialogActions from '@mui/joy/DialogActions'
import { createAsset, deleteAsset, updateAsset, type AssetDto } from './api'

const POPULAR_CURRENCIES = ['USD', 'NZD', 'AUD', 'EUR', 'GBP', 'BTC', 'ETH']

/** Modal for creating a new asset. */
export const CreateAssetModal = ({
  open,
  onClose,
  onCreated,
}: {
  open: boolean
  onClose: () => void
  onCreated: (asset: AssetDto) => void
}) => {
  const [name, setName] = useState('')
  const [currency, setCurrency] = useState('USD')
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleCreate = async () => {
    if (!name.trim()) {
      setError('Asset name is required.')
      return
    }
    setCreating(true)
    setError(null)
    try {
      const res = await createAsset({ name: name.trim(), currency })
      const newAsset: AssetDto = { id: res.assetId, name: name.trim(), ownerId: '', currency, staleAfter: 0 }
      setName('')
      setCurrency('USD')
      onCreated(newAsset)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create asset')
    } finally {
      setCreating(false)
    }
  }

  return (
    <Modal open={open} onClose={() => !creating && onClose()}>
      <ModalDialog variant="outlined" sx={{ borderRadius: '16px', maxWidth: 420, width: '100%' }}>
        <DialogTitle>Create Asset</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <FormControl size="sm">
              <FormLabel>Name</FormLabel>
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
                placeholder="e.g. Bitcoin holdings"
                autoFocus
              />
            </FormControl>
            <FormControl size="sm">
              <FormLabel>Currency</FormLabel>
              <Input
                value={currency}
                onChange={(e) => setCurrency(e.target.value.toUpperCase())}
                placeholder="USD"
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
            {error && (
              <Typography level="body-xs" color="danger">{error}</Typography>
            )}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button variant="plain" onClick={onClose} disabled={creating}>
            Cancel
          </Button>
          <Button onClick={handleCreate} loading={creating} color="primary">
            Create
          </Button>
        </DialogActions>
      </ModalDialog>
    </Modal>
  )
}

/** Confirmation modal for deleting an asset. */
export const DeleteAssetModal = ({
  asset,
  onClose,
  onDeleted,
}: {
  asset: AssetDto | null
  onClose: () => void
  onDeleted: (id: string) => void
}) => {
  const [deleting, setDeleting] = useState(false)

  const handleDelete = async () => {
    if (!asset) return
    setDeleting(true)
    try {
      await deleteAsset(asset.id)
      onDeleted(asset.id)
    } catch (_e) {
      // keep modal open on error
    } finally {
      setDeleting(false)
    }
  }

  return (
    <Modal open={!!asset} onClose={() => !deleting && onClose()}>
      <ModalDialog variant="outlined" sx={{ borderRadius: '16px', maxWidth: 400 }}>
        <DialogTitle>Delete Asset</DialogTitle>
        <DialogContent>
          <Typography level="body-sm" sx={{ color: 'neutral.600' }}>
            Are you sure you want to delete <strong>{asset?.name}</strong>?
            This will also delete all associated transactions and cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button variant="plain" onClick={onClose} disabled={deleting}>
            Cancel
          </Button>
          <Button color="danger" onClick={handleDelete} loading={deleting}>
            Delete
          </Button>
        </DialogActions>
      </ModalDialog>
    </Modal>
  )
}

/** Modal for editing an asset. */
export const EditAssetModal = ({
  asset,
  onClose,
  onUpdated,
}: {
  asset: AssetDto | null
  onClose: () => void
  onUpdated: (updated: AssetDto) => void
}) => {
  const [name, setName] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (asset) setName(asset.name)
  }, [asset])

  const handleSave = async () => {
    if (!asset) return
    if (!name.trim()) {
      setError('Asset name is required.')
      return
    }
    if (name.trim() === asset.name) {
      onClose()
      return
    }
    setSaving(true)
    setError(null)
    try {
      const updated = await updateAsset(asset.id, { name: name.trim() })
      onUpdated(updated)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to update asset')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open={!!asset} onClose={() => !saving && onClose()}>
      <ModalDialog variant="outlined" sx={{ borderRadius: '16px', maxWidth: 420, width: '100%' }}>
        <DialogTitle>Edit Asset</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <FormControl size="sm">
              <FormLabel>Name</FormLabel>
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSave()}
                autoFocus
              />
            </FormControl>
            {error && (
              <Typography level="body-xs" color="danger">{error}</Typography>
            )}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button variant="plain" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={handleSave} loading={saving} color="primary">
            Save
          </Button>
        </DialogActions>
      </ModalDialog>
    </Modal>
  )
}
