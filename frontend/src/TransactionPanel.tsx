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
import ListItemContent from '@mui/joy/ListItemContent'
import Sheet from '@mui/joy/Sheet'
import Chip from '@mui/joy/Chip'
import CircularProgress from '@mui/joy/CircularProgress'
import Modal from '@mui/joy/Modal'
import ModalDialog from '@mui/joy/ModalDialog'
import DialogTitle from '@mui/joy/DialogTitle'
import DialogContent from '@mui/joy/DialogContent'
import DialogActions from '@mui/joy/DialogActions'
import Divider from '@mui/joy/Divider'
import {
  createTransaction,
  deleteTransaction,
  fetchTransactions,
  type TransactionType,
  type TransactionDto,
  type CreateTransactionResponse,
} from './api'
import { useAuth } from './AuthContext'

interface TransactionPanelProps {
  assetId: string
  currency: string
  onTransactionChange: (staleAfter: number) => void
}

const PAGE_SIZE = 10

function formatDate(epochMs: number, tz?: string) {
  return new Date(epochMs).toLocaleDateString('en-US', {
    month: 'short', day: 'numeric', year: 'numeric',
    timeZone: tz,
  })
}

function formatTime(epochMs: number, tz?: string) {
  return new Date(epochMs).toLocaleTimeString('en-US', {
    hour: 'numeric', minute: '2-digit',
    timeZone: tz,
    timeZoneName: 'short',
  })
}
function formatFullDateTime(epochMs: number, tz?: string) {
  return new Date(epochMs).toLocaleString('en-US', {
    weekday: 'long', month: 'long', day: 'numeric', year: 'numeric',
    hour: 'numeric', minute: '2-digit', second: '2-digit',
    timeZone: tz,
    timeZoneName: 'short',
  })
}

/** Build page numbers with ellipsis for large page counts. Always shows first, last, and neighbors of current. */
function paginationRange(current: number, total: number): (number | 'ellipsis')[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i)
  const pages = new Set<number>()
  pages.add(0)
  pages.add(total - 1)
  for (let i = Math.max(0, current - 1); i <= Math.min(total - 1, current + 1); i++) pages.add(i)
  const sorted = [...pages].sort((a, b) => a - b)
  const result: (number | 'ellipsis')[] = []
  for (let i = 0; i < sorted.length; i++) {
    if (i > 0 && sorted[i] - sorted[i - 1] > 1) result.push('ellipsis')
    result.push(sorted[i])
  }
  return result
}

export const TransactionPanel = ({ assetId, currency, onTransactionChange }: TransactionPanelProps) => {
  const { user } = useAuth()
  const tz = user?.timezone
  const [type, setType] = useState<TransactionType>('Trade')
  const [amount, setAmount] = useState('')
  const [unitPrice, setUnitPrice] = useState('')
  const [timestamp, setTimestamp] = useState(() => {
    const d = new Date()
    d.setMinutes(d.getMinutes() - d.getTimezoneOffset())
    return d.toISOString().slice(0, 16)
  })
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [transactions, setTransactions] = useState<TransactionDto[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loadingList, setLoadingList] = useState(true)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [selectedTx, setSelectedTx] = useState<TransactionDto | null>(null)

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

  const parsedAmount = amount ? parseFloat(amount) : undefined
  const parsedUnitPrice = unitPrice ? parseFloat(unitPrice) : undefined
  const computedTotalValue = parsedAmount != null && parsedUnitPrice != null
    ? Math.abs(parsedAmount) * parsedUnitPrice
    : undefined

  const handleCreate = async () => {
    if (!amount) {
      setError(type === 'Trade' ? 'Provide the number of units.' : 'Provide the total units held.')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const ts = new Date(timestamp).toISOString()
      const res: CreateTransactionResponse = await createTransaction(assetId, {
        type,
        amount: parsedAmount,
        value: computedTotalValue,
        timestamp: ts,
      })
      setAmount('')
      setUnitPrice('')
      onTransactionChange(res.staleAfter)
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
      const newTotal = totalElements - 1
      const newTotalPages = Math.max(1, Math.ceil(newTotal / PAGE_SIZE))
      const targetPage = page >= newTotalPages ? Math.max(0, newTotalPages - 1) : page
      await loadTransactions(targetPage)
      if (selectedTx?.id === txId) setSelectedTx(null)
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
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, minHeight: 0 }}>
      {/* Create form */}
      <Sheet variant="outlined" sx={{ borderRadius: '12px', p: 2, flexShrink: 0 }}>
        <Typography level="title-sm" sx={{ mb: 1.5 }}>
          Add Transaction
        </Typography>
        <Box sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <FormControl size="sm" sx={{ minWidth: 160 }}>
            <FormLabel sx={{ fontSize: '11px' }}>Type</FormLabel>
            <Select
              value={type}
              onChange={(_, v) => v && setType(v)}
              size="sm"
              renderValue={(option) => option?.value}
            >
              <Option value="Trade">
                <ListItemContent>
                  <Typography level="body-sm" fontWeight={600}>Trade</Typography>
                  <Typography level="body-xs" sx={{ color: 'neutral.500' }}>Buy or sell units</Typography>
                </ListItemContent>
              </Option>
              <Option value="Reconciliation">
                <ListItemContent>
                  <Typography level="body-sm" fontWeight={600}>Reconciliation</Typography>
                  <Typography level="body-xs" sx={{ color: 'neutral.500' }}>Set total units held</Typography>
                </ListItemContent>
              </Option>
            </Select>
          </FormControl>

          <FormControl size="sm" sx={{ flex: 1, minWidth: 120 }}>
            <FormLabel sx={{ fontSize: '11px' }}>
              {type === 'Trade' ? 'Units (+buy, −sell)' : 'Total units held'}
            </FormLabel>
            <Input value={amount} onChange={(e) => setAmount(e.target.value)} type="number" placeholder="0.00" size="sm" />
          </FormControl>

          <FormControl size="sm" sx={{ flex: 1, minWidth: 120 }}>
            <FormLabel sx={{ fontSize: '11px' }}>Unit price ({currency})</FormLabel>
            <Input value={unitPrice} onChange={(e) => setUnitPrice(e.target.value)} type="number" placeholder="0.00" size="sm" />
          </FormControl>

          <FormControl size="sm" sx={{ flex: 1, minWidth: 180 }}>
            <FormLabel sx={{ fontSize: '11px' }}>Date & Time</FormLabel>
            <Input value={timestamp} onChange={(e) => setTimestamp(e.target.value)} type="datetime-local" size="sm" />
          </FormControl>

          <Button onClick={handleCreate} loading={submitting} size="sm" variant="soft" color="primary" sx={{ whiteSpace: 'nowrap' }}>
            {type === 'Trade' ? 'Add Trade' : 'Add Reconciliation'}
          </Button>
        </Box>

        {computedTotalValue != null && type === 'Trade' && (
          <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.5, mt: 1, px: 0.5 }}>
            <Typography level="body-xs" sx={{ color: 'neutral.500' }}>Total value:</Typography>
            <Typography level="body-xs" sx={{ fontWeight: 700 }}>
              {computedTotalValue.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })} {currency}
            </Typography>
          </Box>
        )}

        {type === 'Reconciliation' && (
          <Typography level="body-xs" sx={{ color: 'neutral.500', mt: 1, px: 0.5 }}>
            Sets the absolute number of units you hold at this time.
          </Typography>
        )}

        {error && (
          <Typography level="body-xs" color="danger" sx={{ mt: 1 }}>{error}</Typography>
        )}
      </Sheet>

      {/* Transaction list */}
      <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: 0, flex: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', px: 0.5, mb: 1, flexShrink: 0 }}>
          <Typography level="body-xs" sx={{ color: 'neutral.500' }}>
            {loadingList ? 'Loading…' : `${totalElements} transaction${totalElements !== 1 ? 's' : ''}`}
          </Typography>
        </Box>

        {loadingList ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
            <CircularProgress size="sm" />
          </Box>
        ) : transactions.length === 0 ? (
          <Box sx={{ py: 3, textAlign: 'center' }}>
            <Typography level="body-sm" sx={{ color: 'neutral.500' }}>
              No transactions yet. Add one above to get started.
            </Typography>
          </Box>
        ) : (
          <>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, flex: 1, minHeight: 0 }}>
              {transactions.map((tx) => {
                const isReconciliation = tx.type === 'Reconciliation'
                const unitPrice = tx.amount != null && tx.value != null && tx.amount !== 0
                  ? tx.value / Math.abs(tx.amount)
                  : null
                return (
                  <Box
                    key={tx.id}
                    onClick={() => setSelectedTx(tx)}
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 1.5,
                      px: 1.5,
                      py: 1,
                      borderRadius: '10px',
                      cursor: 'pointer',
                      '&:hover': { bgcolor: 'neutral.50' },
                    }}
                  >
                    {/* Left: type + date */}
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Chip
                          size="sm"
                          variant="soft"
                          color={isReconciliation ? 'neutral' : 'primary'}
                          sx={{ fontSize: '10px', flexShrink: 0 }}
                        >
                          {tx.type}
                        </Chip>
                        <Typography level="body-xs" sx={{ color: 'neutral.500', fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>
                          {formatDate(tx.timestamp, tz)}, {formatTime(tx.timestamp, tz)}
                        </Typography>
                      </Box>
                      {/* Amount + unit price on second line */}
                      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.75, mt: 0.25 }}>
                        {tx.amount != null && (
                          <Typography level="body-sm" sx={{
                            fontWeight: 600,
                            fontVariantNumeric: 'tabular-nums',
                            color: isReconciliation ? 'neutral.700' : (tx.amount >= 0 ? '#059669' : '#dc2626'),
                          }}>
                            {isReconciliation ? '→ ' : (tx.amount >= 0 ? '+' : '')}
                            {tx.amount.toLocaleString(undefined, { maximumFractionDigits: 4 })} units
                          </Typography>
                        )}
                        {unitPrice != null && (
                          <Typography level="body-xs" sx={{ color: 'neutral.500', fontVariantNumeric: 'tabular-nums' }}>
                            @ {unitPrice.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 })} {currency}
                          </Typography>
                        )}
                      </Box>
                    </Box>

                    {/* Right: total value */}
                    <Box sx={{ textAlign: 'right', flexShrink: 0 }}>
                      {tx.value != null ? (
                        <>
                          <Typography level="body-sm" sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
                            {tx.value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                          </Typography>
                          <Typography level="body-xs" sx={{ color: 'neutral.400', fontSize: '10px' }}>
                            {currency}
                          </Typography>
                        </>
                      ) : (
                        <Typography level="body-xs" sx={{ color: 'neutral.400' }}>—</Typography>
                      )}
                    </Box>
                  </Box>
                )
              })}
            </Box>

            {/* Pagination */}
            {totalPages > 1 && (
              <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 0.5, pt: 1.5, flexShrink: 0 }}>
                <IconButton
                  size="sm"
                  variant="plain"
                  disabled={page === 0}
                  onClick={() => goToPage(page - 1)}
                  sx={{ minWidth: 28, minHeight: 28 }}
                >
                  <span style={{ fontSize: 14 }}>‹</span>
                </IconButton>
                {paginationRange(page, totalPages).map((item, idx) =>
                  item === 'ellipsis' ? (
                    <Typography key={`e${idx}`} level="body-xs" sx={{ color: 'neutral.400', px: 0.5, userSelect: 'none' }}>…</Typography>
                  ) : (
                    <Button
                      key={item}
                      size="sm"
                      variant={item === page ? 'soft' : 'plain'}
                      color={item === page ? 'primary' : 'neutral'}
                      onClick={() => goToPage(item)}
                      sx={{ minWidth: 28, minHeight: 28, px: 0.5, fontSize: '12px' }}
                    >
                      {item + 1}
                    </Button>
                  )
                )}
                <IconButton
                  size="sm"
                  variant="plain"
                  disabled={page >= totalPages - 1}
                  onClick={() => goToPage(page + 1)}
                  sx={{ minWidth: 28, minHeight: 28 }}
                >
                  <span style={{ fontSize: 14 }}>›</span>
                </IconButton>
              </Box>
            )}
          </>
        )}
      </Box>

      {/* Transaction detail modal */}
      <Modal open={!!selectedTx} onClose={() => setSelectedTx(null)}>
        <ModalDialog variant="outlined" sx={{ borderRadius: '16px', maxWidth: 420, width: '90%' }}>
          <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              Transaction Details
              {selectedTx && (
                <Chip
                  size="sm"
                  variant="soft"
                  color={selectedTx.type === 'Trade' ? 'primary' : 'neutral'}
                >
                  {selectedTx.type}
                </Chip>
              )}
            </Box>
          </DialogTitle>
          <Divider />
          {selectedTx && (
            <DialogContent>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
                <DetailRow label="ID" value={selectedTx.id} mono />
                <DetailRow label="Type" value={selectedTx.type} />
                <DetailRow
                  label="Amount"
                  value={selectedTx.amount != null
                    ? `${selectedTx.amount >= 0 ? '+' : ''}${selectedTx.amount.toLocaleString(undefined, { maximumFractionDigits: 6 })} units`
                    : 'Not set'}
                  color={selectedTx.amount != null ? (selectedTx.amount >= 0 ? '#059669' : '#dc2626') : undefined}
                />
                <DetailRow
                  label="Value"
                  value={selectedTx.value != null
                    ? `$${selectedTx.value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 6 })}`
                    : 'Not set'}
                />
                {selectedTx.amount != null && selectedTx.value != null && selectedTx.amount !== 0 && (
                  <DetailRow
                    label="Unit Price"
                    value={`$${(selectedTx.value / Math.abs(selectedTx.amount)).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 })}`}
                  />
                )}
                <Divider />
                <DetailRow label="Date & Time" value={formatFullDateTime(selectedTx.timestamp, tz)} />
                <DetailRow label="Asset ID" value={selectedTx.assetId} mono />
              </Box>
            </DialogContent>
          )}
          <Divider />
          <DialogActions>
            <Button variant="plain" color="neutral" onClick={() => setSelectedTx(null)}>
              Close
            </Button>
            {selectedTx && (
              <Button
                color="danger"
                variant="soft"
                loading={deletingId === selectedTx.id}
                onClick={() => selectedTx && handleDelete(selectedTx.id)}
              >
                Delete
              </Button>
            )}
          </DialogActions>
        </ModalDialog>
      </Modal>
    </Box>
  )
}

function DetailRow({ label, value, mono, color }: { label: string; value: string; mono?: boolean; color?: string }) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 2 }}>
      <Typography level="body-sm" sx={{ color: 'neutral.500', flexShrink: 0 }}>
        {label}
      </Typography>
      <Typography
        level="body-sm"
        sx={{
          fontWeight: 600,
          textAlign: 'right',
          wordBreak: 'break-all',
          ...(mono ? { fontFamily: 'monospace', fontSize: '11px' } : {}),
          ...(color ? { color } : {}),
        }}
      >
        {value}
      </Typography>
    </Box>
  )
}
