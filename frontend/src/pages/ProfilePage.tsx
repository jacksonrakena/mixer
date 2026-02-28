import { useState, useEffect, type FormEvent } from 'react'
import Box from '@mui/joy/Box'
import Sheet from '@mui/joy/Sheet'
import Typography from '@mui/joy/Typography'
import Input from '@mui/joy/Input'
import Button from '@mui/joy/Button'
import Select from '@mui/joy/Select'
import Option from '@mui/joy/Option'
import Autocomplete from '@mui/joy/Autocomplete'
import Alert from '@mui/joy/Alert'
import Chip from '@mui/joy/Chip'
import Divider from '@mui/joy/Divider'
import { useAuth } from '../AuthContext'
import { updateProfile, fetchConfig, type SupportedCurrency } from '../api'

const TIMEZONES: string[] = Intl.supportedValuesOf('timeZone')

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
  const [currencies, setCurrencies] = useState<string[]>([])

  useEffect(() => {
    fetchConfig().then((cfg) => setCurrencies(cfg.currencies)).catch(console.error)
  }, [])

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
      <Typography level="h3" sx={{ fontWeight: 700, mb: 3 }}>
        Profile
      </Typography>

      <Sheet
        variant="outlined"
        sx={{
          p: 3,
          borderRadius: '16px',
          mb: 3,
        }}
      >
        <Typography level="body-xs" sx={{ color: 'neutral.500', textTransform: 'uppercase', letterSpacing: '0.08em', mb: 1, fontWeight: 600 }}>
          Account
        </Typography>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1 }}>
          <Typography level="body-md">
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

        <Typography level="body-xs" sx={{ color: 'neutral.500', mt: 1 }}>
          Member since {new Date(user.createdAt).toLocaleDateString()}
        </Typography>
      </Sheet>

      <Sheet
        variant="outlined"
        sx={{
          p: 3,
          borderRadius: '16px',
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
            sx={{ mb: 2 }}
          />

          <Typography level="body-sm" sx={{ color: 'neutral.400', mb: 0.5 }}>
            Timezone
          </Typography>
          <Autocomplete
            value={timezone}
            onChange={(_, val) => val && setTimezone(val)}
            options={TIMEZONES}
            placeholder="Select timezone…"
            size="sm"
            sx={{ mb: 2 }}
          />

          <Typography level="body-sm" sx={{ color: 'neutral.400', mb: 0.5 }}>
            Default Display Currency
          </Typography>
          <Select
            value={displayCurrency}
            onChange={(_, val) => val && setDisplayCurrency(val as SupportedCurrency)}
            size="sm"
            sx={{ mb: 3 }}
          >
            {currencies.map((cur) => (
              <Option key={cur} value={cur}>
                {cur}
              </Option>
            ))}
          </Select>

          <Divider sx={{ mb: 2 }} />

          <Button
            type="submit"
            loading={saving}
            sx={{
              background: 'linear-gradient(135deg, #3b82f6, #2563eb)',
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
