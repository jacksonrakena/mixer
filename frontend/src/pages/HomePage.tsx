import { useEffect, useState, useMemo, useCallback } from 'react'
import Box from '@mui/joy/Box'
import Typography from '@mui/joy/Typography'
import Sheet from '@mui/joy/Sheet'
import Card from '@mui/joy/Card'
import CardContent from '@mui/joy/CardContent'
import Chip from '@mui/joy/Chip'
import CircularProgress from '@mui/joy/CircularProgress'
import Alert from '@mui/joy/Alert'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../AuthContext'
import {
  fetchPortfolioAggregation,
  fetchAllPortfolioAggregation,
  daysAgo,
  today,
  type PortfolioAggregationPoint,
  type AssetDto,
  type SupportedCurrency,
} from '../api'
import { InteractiveChart, type DragInfo } from '../components/InteractiveChart'
import { DateRangeSelector, DATE_RANGES, type DateRange } from '../components/DateRangeSelector'

const CHART_HEIGHT = 340

function fillDateRange(
  data: PortfolioAggregationPoint[],
  startIso: string,
  endIso: string,
): PortfolioAggregationPoint[] {
  const byDate = new Map<string, PortfolioAggregationPoint>()
  for (const d of data) byDate.set(d.date.slice(0, 10), d)

  const result: PortfolioAggregationPoint[] = []
  const cursor = new Date(startIso + 'T00:00:00Z')
  const end = new Date(endIso + 'T00:00:00Z')
  let lastValue = 0
  let lastCurrency = ''
  let lastBreakdown: PortfolioAggregationPoint['assetBreakdown'] = []
  let hasSeenData = false

  while (cursor <= end) {
    const key = cursor.toISOString().slice(0, 10)
    const existing = byDate.get(key)
    if (existing) {
      hasSeenData = true
      lastValue = existing.totalValue
      lastCurrency = existing.displayCurrency
      lastBreakdown = existing.assetBreakdown
      result.push(existing)
    } else if (hasSeenData) {
      result.push({
        date: key,
        totalValue: lastValue,
        displayCurrency: lastCurrency,
        assetCount: lastBreakdown.length,
        assetBreakdown: lastBreakdown,
      })
    }
    cursor.setUTCDate(cursor.getUTCDate() + 1)
  }
  return result
}

interface HomePageProps {
  displayCurrency: SupportedCurrency
  assets: AssetDto[]
  refreshAssets: () => Promise<AssetDto[]>
}

export default function HomePage({ displayCurrency, assets: propAssets, refreshAssets }: HomePageProps) {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [data, setData] = useState<PortfolioAggregationPoint[]>([])
  const [assets, setAssets] = useState<AssetDto[]>(propAssets)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [range, setRange] = useState<DateRange>('30d')
  const [dragInfo, setDragInfo] = useState<DragInfo | null>(null)

  const chartRange = useMemo(() => {
    if (range === 'all') return null
    const days = DATE_RANGES.find((r) => r.value === range)?.days ?? 30
    return { start: daysAgo(days), end: today() }
  }, [range])

  const loadData = useCallback(() => {
    setLoading(true)
    setError(null)

    const promise = chartRange
      ? fetchPortfolioAggregation(chartRange.start, chartRange.end, displayCurrency)
      : fetchAllPortfolioAggregation(displayCurrency)

    promise
      .then((d) => {
        const sorted = [...d].sort((a, b) => a.date.localeCompare(b.date))
        if (chartRange) {
          setData(fillDateRange(sorted, chartRange.start, chartRange.end))
        } else if (sorted.length > 0) {
          setData(fillDateRange(sorted, sorted[0].date, sorted[sorted.length - 1].date))
        } else {
          setData([])
        }
      })
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to fetch portfolio'))
      .finally(() => setLoading(false))
  }, [chartRange, displayCurrency])

  useEffect(() => { loadData() }, [loadData])
  useEffect(() => {
    setAssets(propAssets)
  }, [propAssets])

  // Portfolio is stale if any asset has never been aggregated or has staleAfter > 0
  const isStale = useMemo(() =>
    assets.some((a) => a.aggregatedThrough === null || a.staleAfter > 0),
    [assets],
  )

  // Poll assets for staleness resolution
  useEffect(() => {
    if (!isStale) return
    const interval = setInterval(async () => {
      try {
        const refreshed = await refreshAssets()
        setAssets(refreshed)
        if (!refreshed.some((a) => a.aggregatedThrough === null || a.staleAfter > 0)) {
          loadData()
        }
      } catch {
        // Silently ignore polling errors
      }
    }, 2000)
    return () => clearInterval(interval)
  }, [isStale, loadData])

  const currentValue = data.length > 0 ? data[data.length - 1].totalValue : null
  const latestDate = data.length > 0 ? new Date(data[data.length - 1].date).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : null
  const firstValue = data.length > 0 ? data[0].totalValue : null
  const change = currentValue !== null && firstValue !== null && firstValue !== 0
    ? ((currentValue - firstValue) / firstValue) * 100
    : null
  const absChange = currentValue !== null && firstValue !== null ? currentValue - firstValue : null
  const isPositive = change !== null && change >= 0

  const xValues = useMemo(() => data.map((d) => new Date(d.date).getTime()), [data])
  const yValues = useMemo(() => data.map((d) => d.totalValue), [data])

  // Latest breakdown for the asset cards
  const latestBreakdown = data.length > 0 ? data[data.length - 1].assetBreakdown : []

  return (
    <Box sx={{ maxWidth: 1100, mx: 'auto', width: '100%' }}>
      {/* Greeting + portfolio value */}
      <Box sx={{ mb: { xs: 2, sm: 3 } }}>
        <Typography level="h3" sx={{ fontWeight: 700, mb: 1, fontSize: { xs: '1.25rem', sm: '1.5rem' } }}>
          {user ? `Hello, ${user.displayName.split(' ')[0]}` : 'Hello'}
        </Typography>
        <Typography level="body-sm" sx={{ color: 'neutral.500', mb: 0.25 }}>
          Total portfolio value{latestDate && ` as of ${latestDate}`}
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'baseline', gap: { xs: 1, sm: 2 }, flexWrap: 'wrap' }}>
          {dragInfo ? (
            <>
              <Typography level="h4" sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', fontSize: { xs: '1.25rem', sm: '1.5rem' } }}>
                {dragInfo.endVal.toLocaleString(undefined, { maximumFractionDigits: 2 })}
                <Typography component="span" level="body-sm" sx={{ color: 'neutral.500', ml: 0.75 }}>
                  {displayCurrency}
                </Typography>
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                {dragInfo.pctChange !== null && (
                  <Chip size="sm" variant="soft" color={dragInfo.absChange >= 0 ? 'success' : 'danger'} sx={{ fontWeight: 600 }}>
                    {dragInfo.absChange >= 0 ? '+' : ''}{dragInfo.pctChange.toFixed(2)}%
                  </Chip>
                )}
                <Typography level="body-sm" sx={{ color: dragInfo.absChange >= 0 ? '#059669' : '#dc2626', fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
                  {dragInfo.absChange >= 0 ? '+' : '−'}{Math.abs(dragInfo.absChange).toLocaleString(undefined, { maximumFractionDigits: 2 })} {displayCurrency}
                </Typography>
                <Typography level="body-xs" sx={{ color: 'neutral.400' }}>
                  {dragInfo.startDate} → {dragInfo.endDate}
                </Typography>
              </Box>
            </>
          ) : (
            <>
              {currentValue !== null ? (
                <Typography level="h4" sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', fontSize: { xs: '1.25rem', sm: '1.5rem' } }}>
                  {currentValue.toLocaleString(undefined, { maximumFractionDigits: 2 })}
                  <Typography component="span" level="body-sm" sx={{ color: 'neutral.500', ml: 0.75 }}>
                    {displayCurrency}
                  </Typography>
                </Typography>
              ) : (
                <Typography level="h4" sx={{ color: 'neutral.400' }}>—</Typography>
              )}
              {change !== null && absChange !== null && (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                  <Chip size="sm" variant="soft" color={isPositive ? 'success' : 'danger'} sx={{ fontWeight: 600 }}>
                    {isPositive ? '+' : ''}{change.toFixed(2)}%
                  </Chip>
                  <Typography level="body-sm" sx={{ color: isPositive ? '#059669' : '#dc2626', fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
                    {isPositive ? '+' : '−'}{Math.abs(absChange).toLocaleString(undefined, { maximumFractionDigits: 2 })} {displayCurrency}
                  </Typography>
                  <Typography level="body-xs" sx={{ color: 'neutral.400' }}>
                    {DATE_RANGES.find((r) => r.value === range)?.label ?? range}
                  </Typography>
                </Box>
              )}
            </>
          )}
        </Box>
      </Box>

      {/* Main chart card */}
      <Card variant="outlined" sx={{ borderRadius: { xs: '12px', md: '16px' }, mb: 3, boxShadow: '0 1px 3px rgba(0,0,0,0.04), 0 1px 2px rgba(0,0,0,0.06)' }}>
        <CardContent sx={{ p: { xs: 1.5, sm: 2 }, '&:last-child': { pb: { xs: 1.5, sm: 2 } } }}>
          {/* Range selector */}
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 1 }}>
            <DateRangeSelector value={range} onChange={setRange} />
          </Box>

          {/* Chart */}
          {loading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: CHART_HEIGHT }}>
              <CircularProgress size="md" />
            </Box>
          ) : error ? (
            <Alert color="danger" sx={{ borderRadius: '10px' }}>{error}</Alert>
          ) : data.length === 0 ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: CHART_HEIGHT, flexDirection: 'column', gap: 1.5 }}>
              <Typography level="title-md" sx={{ color: 'neutral.600' }}>No data yet</Typography>
              <Typography level="body-sm" sx={{ color: 'neutral.500' }}>
                Create assets and add transactions to see your portfolio chart.
              </Typography>
            </Box>
          ) : (
            <InteractiveChart
              xValues={xValues}
              yValues={yValues}
              currency={displayCurrency}
              label="Portfolio"
              chartRange={chartRange}
              disabled={isStale}
              height={CHART_HEIGHT}
              onDragChange={setDragInfo}
              renderOverlay={() =>
                isStale ? (
                  <Box
                    sx={{
                      position: 'absolute',
                      inset: 0,
                      zIndex: 10,
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      justifyContent: 'center',
                      background: 'rgba(255, 255, 255, 0.85)',
                      backdropFilter: 'blur(4px)',
                      borderRadius: '8px',
                      gap: 1.5,
                    }}
                  >
                    <CircularProgress
                      size="md"
                      sx={{ '--CircularProgress-trackColor': 'var(--joy-palette-primary-100)' }}
                    />
                    <Typography level="body-sm" sx={{ color: 'neutral.700', fontWeight: 600 }}>
                      Recalculating…
                    </Typography>
                    <Typography level="body-xs" sx={{ color: 'neutral.500', textAlign: 'center', maxWidth: 260 }}>
                      Portfolio data is being processed. The chart will update automatically.
                    </Typography>
                  </Box>
                ) : null
              }
              renderTooltip={(index, x, y, containerWidth) => {
                const point = data[index]
                if (!point) return null
                const tooltipWidth = 280
                const flipX = x + tooltipWidth + 20 > containerWidth
                return (
                  <Box sx={{
                    position: 'absolute', top: Math.max(y - 20, 0),
                    left: flipX ? undefined : x + 14,
                    right: flipX ? containerWidth - x + 14 : undefined,
                    zIndex: 20, pointerEvents: 'none', minWidth: tooltipWidth,
                    background: 'rgba(255,255,255,0.97)', border: '1px solid rgba(0,0,0,0.1)',
                    borderRadius: '10px', backdropFilter: 'blur(16px)',
                    boxShadow: '0 8px 32px rgba(0,0,0,0.12)', p: 1.5,
                  }}>
                    <Typography level="body-xs" sx={{ color: 'rgba(0,0,0,0.45)', mb: 0.75, fontWeight: 600 }}>
                      {new Date(point.date).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' })}
                    </Typography>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.75 }}>
                      <Typography level="body-sm" sx={{ color: 'rgba(0,0,0,0.55)' }}>Total</Typography>
                      <Typography level="body-sm" sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
                        {point.totalValue.toLocaleString(undefined, { maximumFractionDigits: 2 })} {point.displayCurrency}
                      </Typography>
                    </Box>
                    {point.assetBreakdown.length > 0 && (
                      <Box sx={{ borderTop: '1px solid rgba(0,0,0,0.08)', pt: 0.5 }}>
                        {point.assetBreakdown.map((a) => (
                          <Box key={a.assetId} sx={{ display: 'flex', justifyContent: 'space-between', py: 0.15 }}>
                            <Typography level="body-xs" sx={{ color: 'rgba(0,0,0,0.5)' }}>{a.assetName}</Typography>
                            <Typography level="body-xs" sx={{ fontVariantNumeric: 'tabular-nums', fontWeight: 600 }}>
                              {a.value.toLocaleString(undefined, { maximumFractionDigits: 2 })}
                            </Typography>
                          </Box>
                        ))}
                      </Box>
                    )}
                  </Box>
                )
              }}
            />
          )}
        </CardContent>
      </Card>

      {/* Asset breakdown cards */}
      {latestBreakdown.length > 0 && (
        <Box>
          <Typography level="title-sm" sx={{ color: 'neutral.500', textTransform: 'uppercase', letterSpacing: '0.08em', mb: 1.5, fontWeight: 600 }}>
            Asset Breakdown
          </Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(auto-fill, minmax(240px, 1fr))' }, gap: 1.5 }}>
            {latestBreakdown.map((asset) => {
              const pct = currentValue && currentValue > 0 ? (asset.value / currentValue) * 100 : 0
              const assetInfo = assets.find((a) => a.id === asset.assetId)
              return (
                <Sheet
                  key={asset.assetId}
                  variant="outlined"
                  sx={{
                    borderRadius: '12px', p: { xs: 1.5, sm: 2 }, cursor: 'pointer',
                    transition: 'all 0.15s',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.04), 0 1px 2px rgba(0,0,0,0.06)',
                    '&:hover': { borderColor: 'primary.300', bgcolor: 'primary.50', boxShadow: '0 4px 12px rgba(0,0,0,0.08)' },
                  }}
                  onClick={() => navigate(`/asset/${asset.assetId}`)}
                >
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 1 }}>
                    <Box>
                      <Typography level="title-sm" sx={{ fontWeight: 600 }}>
                        {asset.assetName}
                      </Typography>
                      <Typography level="body-xs" sx={{ color: 'neutral.500' }}>
                        {asset.nativeCurrency}
                        {assetInfo?.currency !== displayCurrency && ` → ${displayCurrency}`}
                      </Typography>
                    </Box>
                    <Chip size="sm" variant="soft" color="neutral" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                      {pct.toFixed(1)}%
                    </Chip>
                  </Box>
                  <Typography level="h4" sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
                    {asset.value.toLocaleString(undefined, { maximumFractionDigits: 2 })}
                    <Typography component="span" level="body-xs" sx={{ color: 'neutral.500', ml: 0.5 }}>
                      {displayCurrency}
                    </Typography>
                  </Typography>
                  {/* Allocation bar */}
                  <Box sx={{ mt: 1, height: 4, borderRadius: 2, bgcolor: 'neutral.100', overflow: 'hidden' }}>
                    <Box sx={{ height: '100%', borderRadius: 2, bgcolor: 'primary.400', width: `${Math.min(pct, 100)}%`, transition: 'width 0.3s' }} />
                  </Box>
                </Sheet>
              )
            })}
          </Box>
        </Box>
      )}
    </Box>
  )
}
