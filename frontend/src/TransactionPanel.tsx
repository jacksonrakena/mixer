import { useState } from 'react'
import Box from '@mui/joy/Box'
import Typography from '@mui/joy/Typography'
import Button from '@mui/joy/Button'
import IconButton from '@mui/joy/IconButton'
import Input from '@mui/joy/Input'
import Select from '@mui/joy/Select'
import Option from '@mui/joy/Option'
import FormLabel from '@mui/joy/FormLabel'
import FormControl from '@mui/joy/FormControl'
import Sheet from '@mui/joy/Sheet'
import Divider from '@mui/joy/Divider'
import Chip from '@mui/joy/Chip'
import CircularProgress from '@mui/joy/CircularProgress'
import {
  createTransaction,
  deleteTransaction,
  type TransactionType,
  type CreateTransactionResponse,
} from './api'

interface TransactionRecord {
  id: string
  type: TransactionType
  amount?: number
  value?: number
  timestamp: string
}

interface TransactionPanelProps {
  assetId: string
  onTransactionChange: () => void
}

const TRANSACTION_TYPES: TransactionType[] = ['Trade', 'Reconciliation']

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
  })
}

export const TransactionPanel = ({ assetId, onTransactionChange }: TransactionPanelProps) => {
  const [transactions, setTransactions] = useState<TransactionRecord[]>([])
  const [type, setType] = useState<TransactionType>('Trade')
  const [amount, setAmount] = useState('')
  const [value, setValue] = useState('')
  const [timestamp, setTimestamp] = useState(() => {
    const d = new Date()
    d.setMinutes(d.getMinutes() - d.getTimezoneOffset())
    return d.toISOString().slice(0, 16)
  })
  const [submitting, setSubmitting] = useState(false)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const handleCreate = async () => {
    if (!amount && !value) {
      setError('Provide at least an amount or value.')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const ts = new Date(timestamp).toISOString()
      const res: CreateTransactionResponse = await createTransaction(assetId, {
        type,
        amount: amount ? parseFloat(amount) : undefined,
        value: value ? parseFloat(value) : undefined,
        timestamp: ts,
      })
      setTransactions((prev) => [
        { id: res.transactionId, type, amount: amount ? parseFloat(amount) : undefined, value: value ? parseFloat(value) : undefined, timestamp: ts },
        ...prev,
      ])
      setAmount('')
      setValue('')
      onTransactionChange()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create transaction')
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async (txId: string) => {
    setDeletingId(txId)
    try {
      await deleteTransaction(assetId, txId)
      setTransactions((prev) => prev.filter((t) => t.id !== txId))
      onTransactionChange()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete transaction')
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
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
          Add Transaction
        </Typography>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          <FormControl size="sm">
            <FormLabel sx={{ color: 'neutral.400', fontSize: '11px' }}>Type</FormLabel>
            <Select
              value={type}
              onChange={(_, v) => v && setType(v)}
              size="sm"
              sx={{
                background: 'rgba(255,255,255,0.05)',
                border: '1px solid rgba(255,255,255,0.1)',
                color: 'white',
              }}
            >
              {TRANSACTION_TYPES.map((t) => (
                <Option key={t} value={t}>{t}</Option>
              ))}
            </Select>
          </FormControl>

          <Box sx={{ display: 'flex', gap: 1 }}>
            <FormControl size="sm" sx={{ flex: 1 }}>
              <FormLabel sx={{ color: 'neutral.400', fontSize: '11px' }}>Amount</FormLabel>
              <Input
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                type="number"
                placeholder="0.00"
                size="sm"
                sx={{
                  background: 'rgba(255,255,255,0.05)',
                  border: '1px solid rgba(255,255,255,0.1)',
                  color: 'white',
                  '& input::placeholder': { color: 'neutral.600' },
                }}
              />
            </FormControl>
            <FormControl size="sm" sx={{ flex: 1 }}>
              <FormLabel sx={{ color: 'neutral.400', fontSize: '11px' }}>Value</FormLabel>
              <Input
                value={value}
                onChange={(e) => setValue(e.target.value)}
                type="number"
                placeholder="0.00"
                size="sm"
                sx={{
                  background: 'rgba(255,255,255,0.05)',
                  border: '1px solid rgba(255,255,255,0.1)',
                  color: 'white',
                  '& input::placeholder': { color: 'neutral.600' },
                }}
              />
            </FormControl>
          </Box>

          <FormControl size="sm">
            <FormLabel sx={{ color: 'neutral.400', fontSize: '11px' }}>Date & Time</FormLabel>
            <Input
              value={timestamp}
              onChange={(e) => setTimestamp(e.target.value)}
              type="datetime-local"
              size="sm"
              sx={{
                background: 'rgba(255,255,255,0.05)',
                border: '1px solid rgba(255,255,255,0.1)',
                color: 'white',
                '& input[type="datetime-local"]::-webkit-calendar-picker-indicator': {
                  filter: 'invert(0.7)',
                },
              }}
            />
          </FormControl>

          {error && (
            <Typography level="body-xs" sx={{ color: 'danger.400' }}>{error}</Typography>
          )}

          <Button
            onClick={handleCreate}
            loading={submitting}
            size="sm"
            sx={{
              background: 'rgba(99,102,241,0.2)',
              color: '#818cf8',
              border: '1px solid rgba(99,102,241,0.3)',
              '&:hover': { background: 'rgba(99,102,241,0.35)' },
            }}
          >
            Add Transaction
          </Button>
        </Box>
      </Sheet>

      {/* Transaction list */}
      {transactions.length > 0 && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
          <Typography level="body-xs" sx={{ color: 'neutral.500', px: 0.5, mb: 0.5 }}>
            Added this session ({transactions.length})
          </Typography>
          {transactions.map((tx, i) => (
            <Box key={tx.id}>
              {i > 0 && <Divider sx={{ borderColor: 'rgba(255,255,255,0.05)', my: 0.5 }} />}
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  px: 1,
                  py: 0.75,
                  borderRadius: '8px',
                  '&:hover': { background: 'rgba(255,255,255,0.03)' },
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Chip
                    size="sm"
                    sx={{
                      background: tx.type === 'Trade'
                        ? 'rgba(96,165,250,0.15)'
                        : 'rgba(167,139,250,0.15)',
                      color: tx.type === 'Trade' ? '#60a5fa' : '#a78bfa',
                      border: 'none',
                      fontSize: '10px',
                    }}
                  >
                    {tx.type}
                  </Chip>
                  <Box>
                    {tx.amount !== undefined && (
                      <Typography level="body-xs" sx={{ color: 'white' }}>
                        {tx.amount} units
                      </Typography>
                    )}
                    <Typography level="body-xs" sx={{ color: 'neutral.500' }}>
                      {formatDate(tx.timestamp)}
                    </Typography>
                  </Box>
                </Box>
                <IconButton
                  size="sm"
                  variant="plain"
                  color="danger"
                  onClick={() => handleDelete(tx.id)}
                  disabled={deletingId === tx.id}
                  sx={{ minWidth: 28, minHeight: 28 }}
                >
                  {deletingId === tx.id
                    ? <CircularProgress size="sm" sx={{ '--CircularProgress-size': '14px' }} />
                    : <span style={{ fontSize: 14 }}>✕</span>}
                </IconButton>
              </Box>
            </Box>
          ))}
        </Box>
      )}
    </Box>
  )
}
