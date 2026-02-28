import { useState, useEffect, type FormEvent } from 'react'
import Box from '@mui/joy/Box'
import Sheet from '@mui/joy/Sheet'
import Typography from '@mui/joy/Typography'
import Input from '@mui/joy/Input'
import Button from '@mui/joy/Button'
import Autocomplete from '@mui/joy/Autocomplete'
import Alert from '@mui/joy/Alert'
import Chip from '@mui/joy/Chip'
import Divider from '@mui/joy/Divider'
import { useAuth } from '../AuthContext'
import { updateProfile, changePassword, fetchConfig, type SupportedCurrency } from '../api'
import CurrencySelect from '../components/CurrencySelect'

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

  // Change password state
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [changingPassword, setChangingPassword] = useState(false)
  const [passwordSuccess, setPasswordSuccess] = useState(false)
  const [passwordError, setPasswordError] = useState('')

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

  const handleChangePassword = async (e: FormEvent) => {
    e.preventDefault()
    setPasswordError('')
    setPasswordSuccess(false)

    if (newPassword.length < 8) {
      setPasswordError('New password must be at least 8 characters')
      return
    }
    if (newPassword !== confirmPassword) {
      setPasswordError('Passwords do not match')
      return
    }

    setChangingPassword(true)
    try {
      await changePassword({ currentPassword, newPassword })
      setPasswordSuccess(true)
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
    } catch (err: unknown) {
      setPasswordError(err instanceof Error ? err.message : 'Failed to change password')
    } finally {
      setChangingPassword(false)
    }
  }

  if (!user) return null

  return (
    <Box sx={{ py: 4, px: 2 }}>
      <Typography level="h3" sx={{ fontWeight: 700, mb: 3 }}>
        Profile
      </Typography>

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' },
          gap: 3,
        }}
      >
        {/* Left column */}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
          {/* Account info */}
          <Sheet variant="outlined" sx={{ p: 3, borderRadius: '16px' }}>
            <Typography level="body-xs" sx={{ color: 'neutral.500', textTransform: 'uppercase', letterSpacing: '0.08em', mb: 1, fontWeight: 600 }}>
              Account
            </Typography>

            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1 }}>
              <Typography level="body-md">{user.email}</Typography>
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
                  <Chip key={role} size="sm" variant="soft" color="primary">{role}</Chip>
                ))}
              </Box>
            )}

            <Typography level="body-xs" sx={{ color: 'neutral.500', mt: 1 }}>
              Member since {new Date(user.createdAt).toLocaleDateString()}
            </Typography>
          </Sheet>

          {/* Settings form */}
          <Sheet variant="outlined" sx={{ p: 3, borderRadius: '16px' }}>
            <Typography level="body-xs" sx={{ color: 'neutral.500', textTransform: 'uppercase', letterSpacing: '0.08em', mb: 2, fontWeight: 600 }}>
              Settings
            </Typography>

            {success && <Alert color="success" sx={{ mb: 2 }}>Profile updated successfully</Alert>}
            {error && <Alert color="danger" sx={{ mb: 2 }}>{error}</Alert>}

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
              <Box sx={{ mb: 3 }}>
                <CurrencySelect
                  value={displayCurrency}
                  onChange={setDisplayCurrency}
                  currencies={currencies}
                />
              </Box>

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

        {/* Right column */}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
          {/* Change password */}
          <Sheet variant="outlined" sx={{ p: 3, borderRadius: '16px' }}>
            <Typography level="body-xs" sx={{ color: 'neutral.500', textTransform: 'uppercase', letterSpacing: '0.08em', mb: 2, fontWeight: 600 }}>
              Change Password
            </Typography>

            {passwordSuccess && <Alert color="success" sx={{ mb: 2 }}>Password changed successfully</Alert>}
            {passwordError && <Alert color="danger" sx={{ mb: 2 }}>{passwordError}</Alert>}

            <form onSubmit={handleChangePassword}>
              <Typography level="body-sm" sx={{ color: 'neutral.400', mb: 0.5 }}>
                Current Password
              </Typography>
              <Input
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                required
                sx={{ mb: 2 }}
              />

              <Typography level="body-sm" sx={{ color: 'neutral.400', mb: 0.5 }}>
                New Password
              </Typography>
              <Input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                required
                sx={{ mb: 2 }}
              />

              <Typography level="body-sm" sx={{ color: 'neutral.400', mb: 0.5 }}>
                Confirm New Password
              </Typography>
              <Input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
                sx={{ mb: 3 }}
              />

              <Divider sx={{ mb: 2 }} />

              <Button
                type="submit"
                loading={changingPassword}
                color="neutral"
                variant="outlined"
                sx={{ fontWeight: 700 }}
              >
                Change Password
              </Button>
            </form>
          </Sheet>
        </Box>
      </Box>
    </Box>
  )
}
