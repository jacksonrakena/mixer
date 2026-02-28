import { useEffect, useState, useMemo, useRef, useCallback } from 'react'
import Box from '@mui/joy/Box'
import Typography from '@mui/joy/Typography'
import Sheet from '@mui/joy/Sheet'
import Card from '@mui/joy/Card'
import CardContent from '@mui/joy/CardContent'
import Chip from '@mui/joy/Chip'
import CircularProgress from '@mui/joy/CircularProgress'
import Alert from '@mui/joy/Alert'
import { LineChart } from '@mui/x-charts/LineChart'
import { useNavigate } from 'react-router-dom'
import {
  fetchPortfolioAggregation,
  fetchAllPortfolioAggregation,
  fetchAssets,
  daysAgo,
  today,
  type PortfolioAggregationPoint,
  type AssetDto,
  type SupportedCurrency,
} from '../api'

type DateRange = '7d' | '30d' | '90d' | '1y' | 'all'

const DATE_RANGES: { label: string; value: DateRange; days?: number }[] = [
  { label: '7D', value: '7d', days: 7 },
  { label: '30D', value: '30d', days: 30 },
  { label: '90D', value: '90d', days: 90 },
  { label: '1Y', value: '1y', days: 365 },
  { label: 'All', value: 'all' },
]

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
  let lastValue = data.length > 0 ? data[0].totalValue : 0
  let lastCurrency = data.length > 0 ? data[0].displayCurrency : ''
  let lastBreakdown = data.length > 0 ? data[0].assetBreakdown : []

  while (cursor <= end) {
    const key = cursor.toISOString().slice(0, 10)
    const existing = byDate.get(key)
    if (existing) {
      lastValue = existing.totalValue
      lastCurrency = existing.displayCurrency
      lastBreakdown = existing.assetBreakdown
      result.push(existing)
    } else {
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
}

export default function HomePage({ displayCurrency }: HomePageProps) {
  const navigate = useNavigate()
  const [data, setData] = useState<PortfolioAggregationPoint[]>([])
  const [assets, setAssets] = useState<AssetDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [range, setRange] = useState<DateRange>('30d')
  const containerRef = useRef<HTMLDivElement>(null)

  // Tooltip state
  const [tooltipIndex, setTooltipIndex] = useState<number | null>(null)
  const [mousePos, setMousePos] = useState({ x: 0, y: 0 })

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
    fetchAssets().then(setAssets).catch(() => {})
  }, [])

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
        const refreshed = await fetchAssets()
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

  // Tooltip mouse handling (same pattern as AssetChart)
  const getLinePointXPositions = useCallback((): number[] | null => {
    const container = containerRef.current
    if (!container) return null
    const path = container.querySelector<SVGPathElement>('.MuiLineElement-root')
    if (!path) return null
    const d = path.getAttribute('d')
    if (!d) return null
    const positions: number[] = []
    const re = /([MLC])\s*([-\d.e]+)[,\s]([-\d.e]+)(?:[,\s]([-\d.e]+)[,\s]([-\d.e]+)[,\s]([-\d.e]+)[,\s]([-\d.e]+))?/gi
    let match: RegExpExecArray | null
    while ((match = re.exec(d)) !== null) {
      const cmd = match[1].toUpperCase()
      if (cmd === 'M' || cmd === 'L') positions.push(parseFloat(match[2]))
      else if (cmd === 'C') positions.push(parseFloat(match[6]))
    }
    return positions.length > 0 ? positions : null
  }, [data])

  const handleMouseMove = useCallback((e: React.MouseEvent<HTMLDivElement>) => {
    const container = containerRef.current
    if (!container || data.length === 0) return
    const rect = container.getBoundingClientRect()
    const mouseX = e.clientX - rect.left
    const mouseY = e.clientY - rect.top
    const xPositions = getLinePointXPositions()
    if (!xPositions || xPositions.length === 0) { setTooltipIndex(null); return }
    const firstX = xPositions[0]
    const lastX = xPositions[xPositions.length - 1]
    if (mouseX < firstX - 5 || mouseX > lastX + 5) { setTooltipIndex(null); return }
    let closestIdx = 0, closestDist = Math.abs(mouseX - xPositions[0])
    for (let i = 1; i < xPositions.length; i++) {
      const dist = Math.abs(mouseX - xPositions[i])
      if (dist < closestDist) { closestDist = dist; closestIdx = i }
    }
    setTooltipIndex(closestIdx)
    setMousePos({ x: mouseX, y: mouseY })
  }, [data, getLinePointXPositions])

  const crosshairX = useMemo(() => {
    if (tooltipIndex === null) return null
    const xPositions = getLinePointXPositions()
    if (!xPositions || tooltipIndex >= xPositions.length) return null
    return xPositions[tooltipIndex]
  }, [tooltipIndex, data, getLinePointXPositions])

  return (
    <Box sx={{ maxWidth: 1100, mx: 'auto', width: '100%' }}>
      {/* Hero stat */}
      <Box sx={{ mb: 3 }}>
        <Typography level="body-sm" sx={{ color: 'neutral.500', mb: 0.5 }}>
          Total Portfolio Value
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 2, flexWrap: 'wrap' }}>
          {currentValue !== null ? (
            <Typography level="h2" sx={{ fontWeight: 800, fontVariantNumeric: 'tabular-nums' }}>
              {currentValue.toLocaleString(undefined, { maximumFractionDigits: 2 })}
              <Typography component="span" level="body-md" sx={{ color: 'neutral.500', ml: 1 }}>
                {displayCurrency}
              </Typography>
            </Typography>
          ) : (
            <Typography level="h2" sx={{ color: 'neutral.400' }}>—</Typography>
          )}
          {change !== null && absChange !== null && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
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
        </Box>
      </Box>

      {/* Main chart card */}
      <Card variant="outlined" sx={{ borderRadius: '16px', mb: 3 }}>
        <CardContent>
          {/* Range selector */}
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 1 }}>
            <Box sx={{ display: 'flex', gap: 0.5 }}>
              {DATE_RANGES.map((r) => (
                <Box
                  key={r.value}
                  onClick={() => setRange(r.value)}
                  sx={{
                    px: 1.5, py: 0.5, borderRadius: '8px', cursor: 'pointer',
                    fontSize: '12px', fontWeight: 600, transition: 'all 0.15s',
                    bgcolor: range === r.value ? 'primary.100' : 'transparent',
                    color: range === r.value ? 'primary.700' : 'neutral.500',
                    '&:hover': { bgcolor: 'primary.50', color: 'primary.700' },
                  }}
                >
                  {r.label}
                </Box>
              ))}
            </Box>
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
            <Box
              ref={containerRef}
              onMouseMove={handleMouseMove}
              onMouseLeave={() => setTooltipIndex(null)}
              sx={{ position: 'relative', cursor: isStale ? 'default' : 'crosshair' }}
            >
              {/* Stale data overlay */}
              {isStale && (
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
              )}
              {/* Crosshair */}
              {!isStale && tooltipIndex !== null && crosshairX !== null && (
                <Box sx={{
                  position: 'absolute', top: 10, bottom: 30, left: crosshairX,
                  width: '1px', background: 'rgba(0,0,0,0.15)', pointerEvents: 'none', zIndex: 15,
                }} />
              )}

              {/* Tooltip */}
              {!isStale && tooltipIndex !== null && data[tooltipIndex] && (() => {
                const point = data[tooltipIndex]
                const containerWidth = containerRef.current?.getBoundingClientRect().width ?? 600
                const tooltipWidth = 280
                const flipX = (crosshairX ?? mousePos.x) + tooltipWidth + 20 > containerWidth
                return (
                  <Box sx={{
                    position: 'absolute', top: Math.max(mousePos.y - 20, 0),
                    left: flipX ? undefined : (crosshairX ?? mousePos.x) + 14,
                    right: flipX ? containerWidth - (crosshairX ?? mousePos.x) + 14 : undefined,
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
              })()}

              <LineChart
                height={CHART_HEIGHT}
                series={[{
                  data: yValues,
                  label: 'Portfolio',
                  color: 'var(--joy-palette-primary-500)',
                  showMark: false,
                  area: true,
                }]}
                xAxis={[{
                  data: xValues,
                  scaleType: 'time',
                  valueFormatter: (v) => new Date(v).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
                }]}
                yAxis={[{ width: 70 }]}
                slotProps={{ tooltip: { trigger: 'none' }, axisHighlight: { x: 'none', y: 'none' }, legend: { hidden: true } }}
                sx={{
                  '& .MuiLineElement-root': { strokeWidth: 2 },
                  '& .MuiAreaElement-root': { fillOpacity: 0.1 },
                  '& .MuiChartsAxis-line, & .MuiChartsAxis-tick': { stroke: 'rgba(0,0,0,0.12)' },
                  '& .MuiChartsAxis-tickLabel': { fill: 'rgba(0,0,0,0.45)', fontSize: '11px' },
                  '& .MuiChartsAxis-label': { fill: 'rgba(0,0,0,0.45)' },
                  '& .MuiChartsAxisHighlight-root': { display: 'none' },
                }}
              />
            </Box>
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
                    borderRadius: '12px', p: 2, cursor: 'pointer',
                    transition: 'all 0.15s',
                    '&:hover': { borderColor: 'primary.300', bgcolor: 'primary.50' },
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
