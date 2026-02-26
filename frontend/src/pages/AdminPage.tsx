import { useEffect, useState } from 'react'
import Box from '@mui/joy/Box'
import Sheet from '@mui/joy/Sheet'
import Typography from '@mui/joy/Typography'
import Table from '@mui/joy/Table'
import Chip from '@mui/joy/Chip'
import CircularProgress from '@mui/joy/CircularProgress'
import Alert from '@mui/joy/Alert'
import { fetchAdminUsers, type UserResponse } from '../api'

export default function AdminPage() {
  const [users, setUsers] = useState<UserResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    fetchAdminUsers()
      .then(setUsers)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))
  }, [])

  return (
    <Box sx={{ maxWidth: 900, mx: 'auto', py: 4, px: 2 }}>
      <Typography level="h3" sx={{ color: 'white', fontWeight: 700, mb: 3 }}>
        Admin — Users
      </Typography>

      {error && (
        <Alert color="danger" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Sheet
        sx={{
          borderRadius: '16px',
          background: 'rgba(17,24,39,0.6)',
          border: '1px solid rgba(255,255,255,0.07)',
          backdropFilter: 'blur(12px)',
          overflow: 'auto',
        }}
      >
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
            <CircularProgress size="sm" />
          </Box>
        ) : (
          <Table
            stripe="odd"
            sx={{
              '& th': { color: 'neutral.400', fontWeight: 600, fontSize: '12px', textTransform: 'uppercase', letterSpacing: '0.05em' },
              '& td': { color: 'neutral.200', fontSize: '13px' },
              '& tr:hover td': { background: 'rgba(99,102,241,0.06)' },
            }}
          >
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Verified</th>
                <th>Roles</th>
                <th>Currency</th>
                <th>Timezone</th>
                <th>Joined</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>{u.displayName}</td>
                  <td>{u.email}</td>
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
                      : '—'}
                  </td>
                  <td>{u.displayCurrency}</td>
                  <td>{u.timezone}</td>
                  <td>{new Date(u.createdAt).toLocaleDateString()}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Sheet>
    </Box>
  )
}
