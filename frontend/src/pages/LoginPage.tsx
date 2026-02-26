import { useState, type FormEvent } from 'react'
import { useNavigate, Link as RouterLink } from 'react-router-dom'
import Box from '@mui/joy/Box'
import Sheet from '@mui/joy/Sheet'
import Typography from '@mui/joy/Typography'
import Input from '@mui/joy/Input'
import Button from '@mui/joy/Button'
import Link from '@mui/joy/Link'
import Alert from '@mui/joy/Alert'
import { useAuth } from '../AuthContext'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await login(email, password)
      navigate('/', { replace: true })
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #0a0e1a 0%, #0d1224 50%, #0a0e1a 100%)',
      }}
    >
      <Sheet
        sx={{
          width: 380,
          p: 4,
          borderRadius: '16px',
          background: 'rgba(17,24,39,0.8)',
          border: '1px solid rgba(255,255,255,0.07)',
          backdropFilter: 'blur(12px)',
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 3 }}>
          <Box
            sx={{
              width: 36,
              height: 36,
              borderRadius: '10px',
              background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '16px',
              fontWeight: 800,
              color: 'white',
              boxShadow: '0 0 16px rgba(99,102,241,0.4)',
            }}
          >
            M
          </Box>
          <Typography level="h4" sx={{ color: 'white', fontWeight: 700 }}>
            Sign in to Mixer
          </Typography>
        </Box>

        {error && (
          <Alert color="danger" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        <form onSubmit={handleSubmit}>
          <Typography level="body-sm" sx={{ color: 'neutral.400', mb: 0.5 }}>
            Email
          </Typography>
          <Input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            sx={{ mb: 2, background: 'rgba(255,255,255,0.05)', color: 'white' }}
          />

          <Typography level="body-sm" sx={{ color: 'neutral.400', mb: 0.5 }}>
            Password
          </Typography>
          <Input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            sx={{ mb: 3, background: 'rgba(255,255,255,0.05)', color: 'white' }}
          />

          <Button
            type="submit"
            loading={loading}
            fullWidth
            sx={{
              background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
              fontWeight: 700,
              mb: 2,
            }}
          >
            Sign In
          </Button>
        </form>

        <Typography level="body-sm" sx={{ color: 'neutral.500', textAlign: 'center' }}>
          Don't have an account?{' '}
          <Link component={RouterLink} to="/signup" sx={{ color: '#8b5cf6', fontWeight: 600 }}>
            Sign up
          </Link>
        </Typography>
      </Sheet>
    </Box>
  )
}
