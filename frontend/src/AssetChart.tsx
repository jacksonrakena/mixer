import { useEffect, useState, useMemo, useRef, useCallback } from 'react'
import Box from '@mui/joy/Box'
import Typography from '@mui/joy/Typography'
import CircularProgress from '@mui/joy/CircularProgress'
import Alert from '@mui/joy/Alert'
import Card from '@mui/joy/Card'
import CardContent from '@mui/joy/CardContent'
import Chip from '@mui/joy/Chip'
import { LineChart } from '@mui/x-charts/LineChart'
import { fetchAggregation, daysAgo, today, type AssetAggregation } from './api'

interface AssetChartProps {
  assetId: string
  assetName: string
  currency: string
}

type DateRange = '7d' | '30d' | '90d' | '1y' | 'all'

// Must match LineChart's internal default margins
const CHART_MARGIN = { left: 60, right: 10, top: 10, bottom: 30 }

const DATE_RANGES: { label: string; value: DateRange; days: number }[] = [
  { label: '7D', value: '7d', days: 7 },
  { label: '30D', value: '30d', days: 30 },
  { label: '90D', value: '90d', days: 90 },
  { label: '1Y', value: '1y', days: 365 },
  { label: 'All', value: 'all', days: 3650 },
]

function deltaColor(value: number): string {
  if (value > 0) return '#34d399'
  if (value < 0) return '#f87171'
  return 'rgba(255,255,255,0.4)'
}

/**
 * Fill in missing dates across [startIso, endIso]. Missing days carry forward
 * the last known value with all deltas = 0.
 */
function fillDateRange(
  data: AssetAggregation[],
  startIso: string,
  endIso: string,
): AssetAggregation[] {
  const byDate = new Map<string, AssetAggregation>()
  for (const d of data) {
    byDate.set(d.date.slice(0, 10), d)
  }

  const result: AssetAggregation[] = []
  const cursor = new Date(startIso + 'T00:00:00Z')
  const end = new Date(endIso + 'T00:00:00Z')
  let lastValue = data.length > 0 ? data[0].value : 0

  while (cursor <= end) {
    const key = cursor.toISOString().slice(0, 10)
    const existing = byDate.get(key)
    if (existing) {
      lastValue = existing.value
      result.push(existing)
    } else {
      result.push({
        assetId: data[0]?.assetId ?? '',
        date: cursor.toISOString(),
        amount: lastValue,
        amountDeltaCapitalGains: 0,
        amountDeltaTrades: 0,
        amountDeltaReconciliation: 0,
        amountDeltaOther: 0,
        value: lastValue,
        valueDeltaCapitalGains: 0,
        valueDeltaTrades: 0,
        valueDeltaReconciliation: 0,
        valueDeltaOther: 0,
      })
    }
    cursor.setUTCDate(cursor.getUTCDate() + 1)
  }
  return result
}

interface TooltipState {
  x: number
  y: number
  dataIndex: number
}

const CHART_HEIGHT = 300

export const AssetChart = ({ assetId, assetName, currency }: AssetChartProps) => {
  const [data, setData] = useState<AssetAggregation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [range, setRange] = useState<DateRange>('30d')
  const [tooltip, setTooltip] = useState<TooltipState | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!assetId) return
    const days = DATE_RANGES.find((r) => r.value === range)?.days ?? 30
    const start = daysAgo(days)
    const end = today()

    setLoading(true)
    setError(null)
    setTooltip(null)
    fetchAggregation(assetId, start, end)
      .then((d) => {
        const sorted = [...d].sort(
          (a, b) => new Date(a.date).getTime() - new Date(b.date).getTime(),
        )
        setData(fillDateRange(sorted, start, end))
      })
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to fetch data'))
      .finally(() => setLoading(false))
  }, [assetId, range])

  const currentValue = data.length > 0 ? data[data.length - 1].value : null
  const firstValue = data.length > 0 ? data[0].value : null
  const change =
    currentValue !== null && firstValue !== null && firstValue !== 0
      ? ((currentValue - firstValue) / firstValue) * 100
      : null
  const isPositive = change !== null && change >= 0

  const xValues = useMemo(() => data.map((d) => new Date(d.date).getTime()), [data])
  const yValues = useMemo(() => data.map((d) => d.value), [data])

  const handleMouseMove = useCallback(
    (e: React.MouseEvent<HTMLDivElement>) => {
      const rect = containerRef.current?.getBoundingClientRect()
      if (!rect || xValues.length === 0) return

      const mouseX = e.clientX - rect.left
      const mouseY = e.clientY - rect.top

      // Map pixel x to data index: linear interpolation across the plot area
      const plotLeft = CHART_MARGIN.left
      const plotRight = rect.width - CHART_MARGIN.right
      const plotWidth = plotRight - plotLeft

      if (mouseX < plotLeft || mouseX > plotRight) {
        setTooltip(null)
        return
      }

      const fraction = (mouseX - plotLeft) / plotWidth
      const rawIndex = fraction * (xValues.length - 1)
      const dataIndex = Math.max(0, Math.min(xValues.length - 1, Math.round(rawIndex)))

      setTooltip({ x: mouseX, y: mouseY, dataIndex })
    },
    [xValues],
  )

  const handleMouseLeave = useCallback(() => {
    setTooltip(null)
  }, [])

  // Tooltip data point
  const tooltipPoint =
    tooltip && tooltip.dataIndex >= 0 && tooltip.dataIndex < data.length
      ? data[tooltip.dataIndex]
      : null

  const deltas = tooltipPoint
    ? [
        { label: 'Trades', value: tooltipPoint.amountDeltaTrades },
        { label: 'Reconciliation', value: tooltipPoint.amountDeltaReconciliation },
        { label: 'Capital Gains', value: tooltipPoint.amountDeltaCapitalGains },
        { label: 'Other', value: tooltipPoint.amountDeltaOther },
      ].filter((d) => d.value !== 0)
    : []

  return (
    <Card
      variant="outlined"
      sx={{
        background: 'rgba(17,24,39,0.6)',
        border: '1px solid rgba(255,255,255,0.07)',
        borderRadius: '16px',
        backdropFilter: 'blur(12px)',
      }}
    >
      <CardContent>
        {/* Header */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
          <Box>
            <Typography level="title-lg" sx={{ color: 'white', fontWeight: 700 }}>
              {assetName}
            </Typography>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.5 }}>
              {currentValue !== null && (
                <Typography level="h3" sx={{ color: 'white', fontWeight: 800 }}>
                  {currentValue.toLocaleString(undefined, { maximumFractionDigits: 4 })}
                  <Typography component="span" level="body-sm" sx={{ color: 'neutral.400', ml: 0.5 }}>
                    {currency}
                  </Typography>
                </Typography>
              )}
              {change !== null && (
                <Chip
                  size="sm"
                  sx={{
                    background: isPositive ? 'rgba(52,211,153,0.15)' : 'rgba(248,113,113,0.15)',
                    color: isPositive ? '#34d399' : '#f87171',
                    border: 'none',
                    fontWeight: 600,
                  }}
                >
                  {isPositive ? '+' : ''}{change.toFixed(2)}%
                </Chip>
              )}
            </Box>
          </Box>

          {/* Range selector */}
          <Box sx={{ display: 'flex', gap: 0.5 }}>
            {DATE_RANGES.map((r) => (
              <Box
                key={r.value}
                onClick={() => setRange(r.value)}
                sx={{
                  px: 1.5, py: 0.5,
                  borderRadius: '8px',
                  cursor: 'pointer',
                  fontSize: '12px',
                  fontWeight: 600,
                  transition: 'all 0.15s',
                  background: range === r.value ? 'rgba(99,102,241,0.25)' : 'transparent',
                  color: range === r.value ? '#818cf8' : 'rgba(255,255,255,0.4)',
                  '&:hover': { background: 'rgba(99,102,241,0.15)', color: '#a5b4fc' },
                }}
              >
                {r.label}
              </Box>
            ))}
          </Box>
        </Box>

        {/* Chart body */}
        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: CHART_HEIGHT }}>
            <CircularProgress size="md" />
          </Box>
        ) : error ? (
          <Alert color="danger" sx={{ borderRadius: '10px' }}>{error}</Alert>
        ) : data.length === 0 ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: CHART_HEIGHT }}>
            <Typography level="body-sm" sx={{ color: 'neutral.500' }}>
              No aggregation data yet. Add transactions to generate data.
            </Typography>
          </Box>
        ) : (
          <Box
            ref={containerRef}
            sx={{ position: 'relative' }}
            onMouseMove={handleMouseMove}
            onMouseLeave={handleMouseLeave}
          >
            <LineChart
              height={CHART_HEIGHT}
              series={[
                {
                  data: yValues,
                  label: assetName,
                  color: '#818cf8',
                  showMark: false,
                  area: true,
                },
              ]}
              xAxis={[
                {
                  data: xValues,
                  scaleType: 'time',
                  valueFormatter: (v) =>
                    new Date(v).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
                },
              ]}
              yAxis={[{ width: 60 }]}
              slotProps={{ tooltip: { trigger: 'none' } }}
              sx={{
                '& .MuiLineElement-root': { strokeWidth: 2 },
                '& .MuiAreaElement-root': { fillOpacity: 0.12 },
                '& .MuiChartsAxis-line, & .MuiChartsAxis-tick': { stroke: 'rgba(255,255,255,0.1)' },
                '& .MuiChartsAxis-tickLabel': { fill: 'rgba(255,255,255,0.4)', fontSize: '11px' },
                '& .MuiChartsAxis-label': { fill: 'rgba(255,255,255,0.4)' },
              }}
            />

            {/* Custom tooltip overlay */}
            {tooltip && tooltipPoint && (
              <Box
                sx={{
                  position: 'absolute',
                  left: tooltip.x + 16,
                  top: Math.max(8, tooltip.y - 60),
                  pointerEvents: 'none',
                  zIndex: 50,
                  background: 'rgba(10,14,26,0.97)',
                  border: '1px solid rgba(255,255,255,0.12)',
                  borderRadius: '10px',
                  padding: '12px 16px',
                  minWidth: 200,
                  boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
                  // keep tooltip on screen
                  transform: tooltip.x > 200 ? 'translateX(-110%)' : 'none',
                }}
              >
                <Typography level="body-xs" sx={{ color: 'rgba(255,255,255,0.4)', mb: 0.5 }}>
                  {new Date(tooltipPoint.date).toLocaleDateString('en-US', {
                    month: 'short', day: 'numeric', year: 'numeric',
                  })}
                </Typography>
                <Typography
                  level="title-md"
                  sx={{ color: 'white', fontWeight: 700, mb: deltas.length > 0 ? 1.5 : 0 }}
                >
                  {tooltipPoint.value.toLocaleString(undefined, { maximumFractionDigits: 4 })}
                </Typography>
                {deltas.length > 0 && (
                  <>
                    <Typography
                      level="body-xs"
                      sx={{ color: 'rgba(255,255,255,0.25)', mb: 0.75, textTransform: 'uppercase', letterSpacing: '0.06em' }}
                    >
                      Changes
                    </Typography>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                      {deltas.map((d) => (
                        <Box
                          key={d.label}
                          sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 3 }}
                        >
                          <span style={{ color: 'rgba(255,255,255,0.5)', fontSize: 12 }}>{d.label}</span>
                          <span style={{ color: deltaColor(d.value), fontWeight: 600, fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
                            {d.value > 0 ? '+' : ''}{d.value.toFixed(4)}
                          </span>
                        </Box>
                      ))}
                    </Box>
                  </>
                )}
                {deltas.length === 0 && (
                  <span style={{ color: 'rgba(255,255,255,0.25)', fontSize: 12 }}>No changes this day</span>
                )}
              </Box>
            )}
          </Box>
        )}
      </CardContent>
    </Card>
  )
}
