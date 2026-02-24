import { useState, useEffect, useCallback } from 'react'
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
  fetchTransactions,
  type TransactionType,
  type TransactionDto,
  type CreateTransactionResponse,
} from './api'

interface TransactionPanelProps {
  assetId: string
  onTransactionChange: (staleAfter: number) => void
}

const TRANSACTION_TYPES: TransactionType[] = ['Trade', 'Reconciliation']
const PAGE_SIZE = 10

function formatDate(epochMs: number) {
  return new Date(epochMs).toLocaleDateString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
  })
}

function formatDateTime(epochMs: number) {
  return new Date(epochMs).toLocaleString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
    hour: 'numeric', minute: '2-digit',
  })
}

export const TransactionPanel = ({ assetId, onTransactionChange }: TransactionPanelProps) => {
  // Create form state
  const [type, setType] = useState<TransactionType>('Trade')
  const [amount, setAmount] = useState('')
  const [value, setValue] = useState('')
  const [timestamp, setTimestamp] = useState(() => {
    const d = new Date()
    d.setMinutes(d.getMinutes() - d.getTimezoneOffset())
    return d.toISOString().slice(0, 16)
  })
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Transaction list state
  const [transactions, setTransactions] = useState<TransactionDto[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loadingList, setLoadingList] = useState(true)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const loadTransactions = useCallback(async (p: number) => {
    setLoadingList(true)
    try {
      const res = await fetchTransactions(assetId, p, PAGE_SIZE)
      setTransactions(res.transactions)
      setPage(res.page)
      setTotalPages(res.totalPages)
      setTotalElements(res.totalElements)
    } catch (e) {
      console.error('Failed to load transactions', e)
    } finally {
      setLoadingList(false)
    }
  }, [assetId])

  useEffect(() => {
    setPage(0)
    loadTransactions(0)
  }, [assetId, loadTransactions])

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
      setAmount('')
      setValue('')
      onTransactionChange(res.staleAfter)
      // Reload current page to show updated list
      await loadTransactions(0)
      setPage(0)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create transaction')
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async (txId: string) => {
    setDeletingId(txId)
    try {
      const res = await deleteTransaction(assetId, txId)
      onTransactionChange(res.staleAfter)
      // Reload current page; if page is now empty, go back one page
      const newTotal = totalElements - 1
      const newTotalPages = Math.max(1, Math.ceil(newTotal / PAGE_SIZE))
      const targetPage = page >= newTotalPages ? Math.max(0, newTotalPages - 1) : page
      await loadTransactions(targetPage)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete transaction')
    } finally {
      setDeletingId(null)
    }
  }

  const goToPage = (p: number) => {
    setPage(p)
    loadTransactions(p)
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
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', px: 0.5 }}>
          <Typography level="body-xs" sx={{ color: 'neutral.500' }}>
            {loadingList ? 'Loading…' : `${totalElements} transaction${totalElements !== 1 ? 's' : ''}`}
          </Typography>
          {totalPages > 1 && (
            <Typography level="body-xs" sx={{ color: 'neutral.500' }}>
              Page {page + 1} of {totalPages}
            </Typography>
          )}
        </Box>

        {loadingList ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
            <CircularProgress size="sm" />
          </Box>
        ) : transactions.length === 0 ? (
          <Box sx={{ py: 3, textAlign: 'center' }}>
            <Typography level="body-sm" sx={{ color: 'neutral.600' }}>
              No transactions yet. Add one above to get started.
            </Typography>
          </Box>
        ) : (
          <>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
              {transactions.map((tx, i) => (
                <Box key={tx.id}>
              {i > 0 && <Divider sx={{ borderColor: 'rgba(255,255,255,0.05)', my: 0.5 }} />}
                  <Box
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      px: 1.5,
                      py: 1,
                      borderRadius: '8px',
                  '&:hover': { background: 'rgba(255,255,255,0.03)' },
                    }}
                  >
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, minWidth: 0, flex: 1 }}>
                      <Chip
                        size="sm"
                        sx={{
                          background: tx.type === 'Trade'
                            ? 'rgba(59,130,246,0.1)'
                            : 'rgba(139,92,246,0.1)',
                          color: tx.type === 'Trade' ? '#2563eb' : '#7c3aed',
                          border: 'none',
                          fontSize: '10px',
                          flexShrink: 0,
                        }}
                      >
                        {tx.type}
                      </Chip>
                      <Box sx={{ minWidth: 0 }}>
                        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
                          {tx.amount != null && (
                            <Typography level="body-xs" sx={{ color: tx.amount >= 0 ? '#059669' : '#dc2626', fontWeight: 600 }}>
                              {tx.amount >= 0 ? '+' : ''}{tx.amount} units
                            </Typography>
                          )}
                          {tx.value != null && (
                            <Typography level="body-xs" sx={{ color: 'neutral.300' }}>
                              ${tx.value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                            </Typography>
                          )}
                        </Box>
                        <Typography level="body-xs" sx={{ color: 'neutral.600', fontSize: '10px' }}>
                          {formatDateTime(tx.timestamp)}
                        </Typography>
                      </Box>
                    </Box>
                    <IconButton
                      size="sm"
                      variant="plain"
                      color="danger"
                      onClick={() => handleDelete(tx.id)}
                      disabled={deletingId === tx.id}
                      sx={{ minWidth: 28, minHeight: 28, flexShrink: 0 }}
                    >
                      {deletingId === tx.id
                        ? <CircularProgress size="sm" sx={{ '--CircularProgress-size': '14px' }} />
                        : <span style={{ fontSize: 14 }}>✕</span>}
                    </IconButton>
                  </Box>
                </Box>
              ))}
            </Box>

            {/* Pagination controls */}
            {totalPages > 1 && (
              <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 1, pt: 1 }}>
                <Button
                  size="sm"
                  variant="plain"
                  disabled={page === 0}
                  onClick={() => goToPage(page - 1)}
                  sx={{ color: 'neutral.400', minWidth: 32, '&:hover': { background: 'rgba(0,0,0,0.04)' } }}
                >
                  ‹
                </Button>
                {Array.from({ length: totalPages }, (_, i) => (
                  <Button
                    key={i}
                    size="sm"
                    variant={i === page ? 'soft' : 'plain'}
                    onClick={() => goToPage(i)}
                    sx={{
                      minWidth: 32,
                      color: i === page ? '#059669' : 'neutral.500',
                      background: i === page ? 'rgba(16,185,129,0.1)' : 'transparent',
                      '&:hover': { background: i === page ? 'rgba(16,185,129,0.15)' : 'rgba(0,0,0,0.04)' },
                    }}
                  >
                    {i + 1}
                  </Button>
                ))}
                <Button
                  size="sm"
                  variant="plain"
                  disabled={page >= totalPages - 1}
                  onClick={() => goToPage(page + 1)}
                  sx={{ color: 'neutral.400', minWidth: 32, '&:hover': { background: 'rgba(0,0,0,0.04)' } }}
                >
                  ›
                </Button>
              </Box>
            )}
          </>
        )}
      </Box>
    </Box>
  )
}
