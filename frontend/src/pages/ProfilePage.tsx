import { useState, type FormEvent } from 'react'
import Box from '@mui/joy/Box'
import Sheet from '@mui/joy/Sheet'
import Typography from '@mui/joy/Typography'
import Input from '@mui/joy/Input'
import Button from '@mui/joy/Button'
import Select from '@mui/joy/Select'
import Option from '@mui/joy/Option'
import Alert from '@mui/joy/Alert'
import Chip from '@mui/joy/Chip'
import Divider from '@mui/joy/Divider'
import { useAuth } from '../AuthContext'
import { updateProfile, SUPPORTED_CURRENCIES, type SupportedCurrency } from '../api'

export default function ProfilePage() {
  const { user, refreshUser } = useAuth()
  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [timezone, setTimezone] = useState(user?.timezone ?? '')
  const [displayCurrency, setDisplayCurrency] = useState<SupportedCurrency>(
    (user?.displayCurrency as SupportedCurrency) ?? 'AUD',
  )
  const [saving, setSaving] = useState(false)
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState('')

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setSuccess(false)
    setSaving(true)
    try {
      await updateProfile({ displayName, timezone, displayCurrency })
      await refreshUser()
      setSuccess(true)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update profile')
    } finally {
      setSaving(false)
    }
  }

  if (!user) return null

  return (
    <Box sx={{ maxWidth: 600, mx: 'auto', py: 4, px: 2 }}>
      <Typography level="h3" sx={{ color: 'white', fontWeight: 700, mb: 3 }}>
        Profile
      </Typography>

      <Sheet
        sx={{
          p: 3,
          borderRadius: '16px',
          background: 'rgba(17,24,39,0.6)',
          border: '1px solid rgba(255,255,255,0.07)',
          backdropFilter: 'blur(12px)',
          mb: 3,
        }}
      >
        <Typography level="body-xs" sx={{ color: 'neutral.500', textTransform: 'uppercase', letterSpacing: '0.08em', mb: 1, fontWeight: 600 }}>
          Account
        </Typography>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1 }}>
          <Typography level="body-md" sx={{ color: 'neutral.300' }}>
            {user.email}
          </Typography>
          <Chip
            size="sm"
            variant="soft"
            color={user.emailVerified ? 'success' : 'warning'}
          >
            {user.emailVerified ? 'Verified' : 'Unverified'}
          </Chip>
        </Box>

        {user.roles.length > 0 && (
          <Box sx={{ display: 'flex', gap: 0.5, mt: 1 }}>
            {user.roles.map((role) => (
              <Chip key={role} size="sm" variant="soft" color="primary">
                {role}
              </Chip>
            ))}
          </Box>
        )}

        <Typography level="body-xs" sx={{ color: 'neutral.600', mt: 1 }}>
          Member since {new Date(user.createdAt).toLocaleDateString()}
        </Typography>
      </Sheet>

      <Sheet
        sx={{
          p: 3,
          borderRadius: '16px',
          background: 'rgba(17,24,39,0.6)',
          border: '1px solid rgba(255,255,255,0.07)',
          backdropFilter: 'blur(12px)',
        }}
      >
        <Typography level="body-xs" sx={{ color: 'neutral.500', textTransform: 'uppercase', letterSpacing: '0.08em', mb: 2, fontWeight: 600 }}>
          Settings
        </Typography>

        {success && (
          <Alert color="success" sx={{ mb: 2 }}>
            Profile updated successfully
          </Alert>
        )}
        {error && (
          <Alert color="danger" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        <form onSubmit={handleSubmit}>
          <Typography level="body-sm" sx={{ color: 'neutral.400', mb: 0.5 }}>
            Display Name
          </Typography>
          <Input
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            required
            sx={{ mb: 2, background: 'rgba(255,255,255,0.05)', color: 'white' }}
          />

          <Typography level="body-sm" sx={{ color: 'neutral.400', mb: 0.5 }}>
            Timezone
          </Typography>
          <Input
            value={timezone}
            onChange={(e) => setTimezone(e.target.value)}
            sx={{ mb: 2, background: 'rgba(255,255,255,0.05)', color: 'white' }}
          />

          <Typography level="body-sm" sx={{ color: 'neutral.400', mb: 0.5 }}>
            Default Display Currency
          </Typography>
          <Select
            value={displayCurrency}
            onChange={(_, val) => val && setDisplayCurrency(val as SupportedCurrency)}
            size="sm"
            sx={{ mb: 3, background: 'rgba(255,255,255,0.05)', color: 'white' }}
          >
            {SUPPORTED_CURRENCIES.map((cur) => (
              <Option key={cur} value={cur}>
                {cur}
              </Option>
            ))}
          </Select>

          <Divider sx={{ borderColor: 'rgba(255,255,255,0.06)', mb: 2 }} />

          <Button
            type="submit"
            loading={saving}
            sx={{
              background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
              fontWeight: 700,
            }}
          >
            Save Changes
          </Button>
        </form>
      </Sheet>
    </Box>
  )
}
