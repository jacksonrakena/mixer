import { useEffect, useState, useCallback } from 'react'
import Box from '@mui/joy/Box'
import Sheet from '@mui/joy/Sheet'
import Typography from '@mui/joy/Typography'
import Table from '@mui/joy/Table'
import Chip from '@mui/joy/Chip'
import CircularProgress from '@mui/joy/CircularProgress'
import Alert from '@mui/joy/Alert'
import Button from '@mui/joy/Button'
import Input from '@mui/joy/Input'
import FormControl from '@mui/joy/FormControl'
import FormLabel from '@mui/joy/FormLabel'
import Switch from '@mui/joy/Switch'
import Divider from '@mui/joy/Divider'
import Modal from '@mui/joy/Modal'
import ModalDialog from '@mui/joy/ModalDialog'
import DialogTitle from '@mui/joy/DialogTitle'
import DialogContent from '@mui/joy/DialogContent'
import DialogActions from '@mui/joy/DialogActions'
import {
  fetchAdminUsers,
  adminCreateUser,
  adminDeleteUser,
  adminForceReaggregateAll,
  adminFetchDebugCounts,
  type UserResponse,
  type EntityCounts,
} from '../api'

export default function AdminPage() {
  const [users, setUsers] = useState<UserResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  // Create user form
  const [showCreate, setShowCreate] = useState(false)
  const [createEmail, setCreateEmail] = useState('')
  const [createPassword, setCreatePassword] = useState('')
  const [createName, setCreateName] = useState('')
  const [createVerified, setCreateVerified] = useState(false)
  const [creating, setCreating] = useState(false)

  // Delete user
  const [deleteTarget, setDeleteTarget] = useState<UserResponse | null>(null)
  const [deleting, setDeleting] = useState(false)

  // Aggregations
  const [reaggregating, setReaggregating] = useState(false)

  // Debug
  const [counts, setCounts] = useState<EntityCounts | null>(null)
  const [loadingCounts, setLoadingCounts] = useState(false)

  const loadUsers = useCallback(() => {
    setLoading(true)
    fetchAdminUsers()
      .then(setUsers)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { loadUsers() }, [loadUsers])

  const handleCreateUser = async () => {
    setCreating(true)
    setError('')
    try {
      await adminCreateUser({
        email: createEmail,
        password: createPassword,
        displayName: createName,
        emailVerified: createVerified,
      })
      setCreateEmail('')
      setCreatePassword('')
      setCreateName('')
      setCreateVerified(false)
      setShowCreate(false)
      setSuccess('User created successfully')
      loadUsers()
    } catch (err: any) {
      setError(err.message)
    } finally {
      setCreating(false)
    }
  }

  const handleDeleteUser = async () => {
    if (!deleteTarget) return
    setDeleting(true)
    setError('')
    try {
      await adminDeleteUser(deleteTarget.id)
      setDeleteTarget(null)
      setSuccess('User deleted successfully')
      loadUsers()
    } catch (err: any) {
      setError(err.message)
    } finally {
      setDeleting(false)
    }
  }

  const handleForceReaggregate = async () => {
    setReaggregating(true)
    setError('')
    try {
      const result = await adminForceReaggregateAll()
      setSuccess(`Reaggregation complete. ${result.usersProcessed} user(s) processed.`)
    } catch (err: any) {
      setError(err.message)
    } finally {
      setReaggregating(false)
    }
  }

  const handleLoadCounts = async () => {
    setLoadingCounts(true)
    try {
      setCounts(await adminFetchDebugCounts())
    } catch (err: any) {
      setError(err.message)
    } finally {
      setLoadingCounts(false)
    }
  }

  useEffect(() => { handleLoadCounts() }, [])

  return (
    <Box sx={{ maxWidth: 960, mx: 'auto', py: 4, px: 2, display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Typography level="h3" sx={{ fontWeight: 700 }}>
        Administration
      </Typography>

      {error && (
        <Alert color="danger" variant="soft" sx={{ borderRadius: '10px' }} endDecorator={
          <Button size="sm" variant="plain" color="danger" onClick={() => setError('')}>✕</Button>
        }>
          {error}
        </Alert>
      )}
      {success && (
        <Alert color="success" variant="soft" sx={{ borderRadius: '10px' }} endDecorator={
          <Button size="sm" variant="plain" color="success" onClick={() => setSuccess('')}>✕</Button>
        }>
          {success}
        </Alert>
      )}

      {/* ── Users Section ─────────────────────────────────────────────────── */}
      <Sheet variant="outlined" sx={{ borderRadius: '16px', overflow: 'hidden' }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', p: 2, pb: 0 }}>
          <Typography level="title-md" sx={{ fontWeight: 700 }}>Users</Typography>
          <Button size="sm" variant="soft" color="primary" onClick={() => setShowCreate(!showCreate)}>
            {showCreate ? 'Cancel' : 'Create User'}
          </Button>
        </Box>

        {/* Create user form */}
        {showCreate && (
          <Box sx={{ p: 2 }}>
            <Sheet variant="soft" sx={{ borderRadius: '12px', p: 2 }}>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                <Box sx={{ display: 'flex', gap: 1.5 }}>
                  <FormControl size="sm" sx={{ flex: 1 }}>
                    <FormLabel>Display Name</FormLabel>
                    <Input value={createName} onChange={(e) => setCreateName(e.target.value)} placeholder="John Smith" />
                  </FormControl>
                  <FormControl size="sm" sx={{ flex: 1 }}>
                    <FormLabel>Email</FormLabel>
                    <Input value={createEmail} onChange={(e) => setCreateEmail(e.target.value)} placeholder="user@example.com" type="email" />
                  </FormControl>
                </Box>
                <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-end' }}>
                  <FormControl size="sm" sx={{ flex: 1 }}>
                    <FormLabel>Password</FormLabel>
                    <Input value={createPassword} onChange={(e) => setCreatePassword(e.target.value)} placeholder="Min 8 characters" type="password" />
                  </FormControl>
                  <FormControl size="sm" sx={{ flex: 'none' }}>
                    <FormLabel>Email Verified</FormLabel>
                    <Switch checked={createVerified} onChange={(e) => setCreateVerified(e.target.checked)} size="sm" />
                  </FormControl>
                  <Button
                    size="sm"
                    variant="solid"
                    color="primary"
                    loading={creating}
                    onClick={handleCreateUser}
                    disabled={!createEmail || !createPassword || !createName}
                  >
                    Create
                  </Button>
                </Box>
              </Box>
            </Sheet>
          </Box>
        )}

        {/* Users table */}
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
            <CircularProgress size="sm" />
          </Box>
        ) : (
          <Table
            stripe="odd"
            sx={{
              '& th': { color: 'neutral.600', fontWeight: 600, fontSize: '11px', textTransform: 'uppercase', letterSpacing: '0.04em' },
              '& td': { fontSize: '13px' },
              '& tr:hover td': { bgcolor: 'primary.50' },
            }}
          >
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Verified</th>
                <th>Roles</th>
                <th>Currency</th>
                <th>Joined</th>
                <th style={{ width: 60 }}></th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>{u.displayName}</td>
                  <td>
                    <Typography level="body-xs" sx={{ fontFamily: 'monospace' }}>{u.email}</Typography>
                  </td>
                  <td>
                    <Chip size="sm" variant="soft" color={u.emailVerified ? 'success' : 'warning'}>
                      {u.emailVerified ? 'Yes' : 'No'}
                    </Chip>
                  </td>
                  <td>
                    {u.roles.length > 0
                      ? u.roles.map((r) => (
                          <Chip key={r} size="sm" variant="soft" color="primary" sx={{ mr: 0.5 }}>
                            {r}
                          </Chip>
                        ))
                      : <Typography level="body-xs" sx={{ color: 'neutral.400' }}>—</Typography>}
                  </td>
                  <td>{u.displayCurrency}</td>
                  <td>
                    <Typography level="body-xs" sx={{ color: 'neutral.600' }}>
                      {new Date(u.createdAt).toLocaleDateString()}
                    </Typography>
                  </td>
                  <td>
                    <Button
                      size="sm"
                      variant="plain"
                      color="danger"
                      onClick={() => setDeleteTarget(u)}
                      sx={{ minHeight: 24, fontSize: '11px' }}
                    >
                      Delete
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Sheet>

      {/* ── Aggregations Section ──────────────────────────────────────────── */}
      <Sheet variant="outlined" sx={{ borderRadius: '16px', p: 2 }}>
        <Typography level="title-md" sx={{ fontWeight: 700, mb: 1.5 }}>Aggregations</Typography>
        <Typography level="body-sm" sx={{ color: 'neutral.500', mb: 2 }}>
          Force all asset aggregations to be recomputed for every user. This may take a while for large datasets.
        </Typography>
        <Button
          variant="soft"
          color="warning"
          loading={reaggregating}
          onClick={handleForceReaggregate}
        >
          Force Reaggregate All Users
        </Button>
      </Sheet>

      {/* ── Debug Section ─────────────────────────────────────────────────── */}
      <Sheet variant="outlined" sx={{ borderRadius: '16px', p: 2 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography level="title-md" sx={{ fontWeight: 700 }}>Debug</Typography>
          <Button size="sm" variant="plain" onClick={handleLoadCounts} loading={loadingCounts}>
            Refresh
          </Button>
        </Box>
        {counts ? (
          <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 1.5 }}>
            {(Object.entries(counts) as [string, number][]).map(([key, value]) => (
              <Sheet key={key} variant="soft" sx={{ borderRadius: '10px', p: 1.5, textAlign: 'center' }}>
                <Typography level="h4" sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
                  {value.toLocaleString()}
                </Typography>
                <Typography level="body-xs" sx={{ color: 'neutral.500', textTransform: 'capitalize' }}>
                  {key.replace(/([A-Z])/g, ' $1').trim()}
                </Typography>
              </Sheet>
            ))}
          </Box>
        ) : loadingCounts ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
            <CircularProgress size="sm" />
          </Box>
        ) : null}
      </Sheet>

      {/* ── Delete Confirmation Modal ─────────────────────────────────────── */}
      <Modal open={!!deleteTarget} onClose={() => !deleting && setDeleteTarget(null)}>
        <ModalDialog variant="outlined" sx={{ borderRadius: '16px', maxWidth: 420 }}>
          <DialogTitle>Delete User</DialogTitle>
          <DialogContent>
            <Typography level="body-sm" sx={{ color: 'neutral.600' }}>
              Are you sure you want to delete <strong>{deleteTarget?.displayName}</strong> ({deleteTarget?.email})?
              This will permanently delete all their assets, transactions, and aggregations.
            </Typography>
          </DialogContent>
          <DialogActions>
            <Button variant="plain" color="neutral" onClick={() => setDeleteTarget(null)} disabled={deleting}>
              Cancel
            </Button>
            <Button color="danger" variant="soft" onClick={handleDeleteUser} loading={deleting}>
              Delete User
            </Button>
          </DialogActions>
        </ModalDialog>
      </Modal>
    </Box>
  )
}
