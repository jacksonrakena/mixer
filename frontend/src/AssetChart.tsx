import { useEffect, useState, useMemo, useRef, useCallback } from "react";
import Box from "@mui/joy/Box";
import Typography from "@mui/joy/Typography";
import CircularProgress from "@mui/joy/CircularProgress";
import Alert from "@mui/joy/Alert";
import Card from "@mui/joy/Card";
import CardContent from "@mui/joy/CardContent";
import Chip from "@mui/joy/Chip";
import { LineChart } from "@mui/x-charts/LineChart";
import {
  fetchAggregation,
  fetchAllAggregations,
  fetchAssetStaleness,
  daysAgo,
  today,
  type AssetAggregation,
} from "./api";

// ── Tooltip helpers ──────────────────────────────────────────────────────────

/** Format a number with sign prefix and fixed decimals */
function fmtDelta(v: number, decimals = 4): string {
  const s = Math.abs(v).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: decimals,
  });
  if (v > 0) return `+${s}`;
  if (v < 0) return `−${s}`;
  return s;
}

/** Return green for positive, red for negative, muted for zero */
function deltaStyle(v: number): React.CSSProperties {
  return {
    color: v > 0 ? "#059669" : v < 0 ? "#dc2626" : "rgba(0,0,0,0.25)",
    fontVariantNumeric: "tabular-nums",
  };
}

interface DeltaRow {
  label: string;
  amountDelta: number;
}

/** Check if a data point has any non-zero reconciliation deltas */
function hasReconciliation(d: AssetAggregation): boolean {
  return d.amountDeltaReconciliation !== 0;
}

/** Check if a data point has any non-zero activity (excluding reconciliation) */
function hasActivity(d: AssetAggregation): boolean {
  return d.amountDeltaTrades !== 0 || d.amountDeltaOther !== 0;
}

/** Get the best display value for a data point (prefers displayValue, falls back to nativeValue) */
function getDisplayValue(d: AssetAggregation): number {
  return d.displayValue ?? d.nativeValue;
}

/** Build delta rows for the tooltip (only include non-zero rows) */
function buildDeltaRows(d: AssetAggregation): DeltaRow[] {
  const rows: DeltaRow[] = [];
  if (d.amountDeltaTrades !== 0) {
    rows.push({ label: "Trades", amountDelta: d.amountDeltaTrades });
  }
  if (d.amountDeltaOther !== 0) {
    rows.push({ label: "Other", amountDelta: d.amountDeltaOther });
  }
  return rows;
}

// ── Custom tooltip component ─────────────────────────────────────────────────

interface ChartTooltipProps {
  point: AssetAggregation;
  currency: string;
  x: number;
  y: number;
  containerWidth: number;
}

function ChartTooltip({
  point,
  currency,
  x,
  y,
  containerWidth,
}: ChartTooltipProps) {
  const date = new Date(point.date);
  const dateStr = date.toLocaleDateString("en-US", {
    weekday: "short",
    month: "short",
    day: "numeric",
    year: "numeric",
  });

  const deltaRows = buildDeltaRows(point);
  const showRecon = hasReconciliation(point);
  const showDeltas = hasActivity(point);
  const displayVal = getDisplayValue(point);
  const hasFx = point.fxConversion !== null;
  const effectiveCurrency = point.displayCurrency ?? currency;

  // Position tooltip to avoid overflow — flip to left side if too close to right edge
  const tooltipWidth = 300;
  const flipX = x + tooltipWidth + 20 > containerWidth;

  return (
    <Box
      sx={{
        position: "absolute",
        top: Math.max(y - 20, 0),
        left: flipX ? undefined : x + 14,
        right: flipX ? containerWidth - x + 14 : undefined,
        zIndex: 20,
        pointerEvents: "none",
        minWidth: tooltipWidth,
        background: "rgba(255, 255, 255, 0.97)",
        border: "1px solid rgba(0,0,0,0.1)",
        borderRadius: "10px",
        backdropFilter: "blur(16px)",
        boxShadow: "0 8px 32px rgba(0,0,0,0.12)",
        p: 1.5,
      }}
    >
      {/* Date header */}
      <Typography
        level="body-xs"
        sx={{ color: "rgba(0,0,0,0.45)", mb: 0.75, fontWeight: 600 }}
      >
        {dateStr}
      </Typography>

      {/* Display value (converted) */}
      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 0.5 }}>
        <Typography level="body-sm" sx={{ color: "rgba(0,0,0,0.55)" }}>
          Value
        </Typography>
        <Typography
          level="body-sm"
          sx={{
            color: "#1e293b",
            fontWeight: 700,
            fontVariantNumeric: "tabular-nums",
          }}
        >
          {displayVal.toLocaleString(undefined, { maximumFractionDigits: 2 })}{" "}
          {effectiveCurrency}
        </Typography>
      </Box>

      {/* Native value (if different currency) */}
      {hasFx && (
        <Box sx={{ display: "flex", justifyContent: "space-between", mb: 0.5 }}>
          <Typography level="body-xs" sx={{ color: "rgba(0,0,0,0.4)" }}>
            Native
          </Typography>
          <Typography
            level="body-xs"
            sx={{
              color: "rgba(0,0,0,0.5)",
              fontVariantNumeric: "tabular-nums",
            }}
          >
            {point.nativeValue.toLocaleString(undefined, {
              maximumFractionDigits: 4,
            })}{" "}
            {point.nativeCurrency}
          </Typography>
        </Box>
      )}

      {/* Amount (units held) */}
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          mb: showDeltas || showRecon || hasFx || point.unitPrice != null ? 1 : 0,
        }}
      >
        <Typography level="body-sm" sx={{ color: "rgba(0,0,0,0.55)" }}>
          Amount
        </Typography>
        <Typography
          level="body-sm"
          sx={{
            color: "#1e293b",
            fontWeight: 700,
            fontVariantNumeric: "tabular-nums",
          }}
        >
          {point.amount.toLocaleString(undefined, { maximumFractionDigits: 4 })}
        </Typography>
      </Box>

      {/* Unit price / value date info */}
      {point.unitPrice != null && (
        <Box
          sx={{
            borderTop: "1px solid rgba(0,0,0,0.08)",
            pt: 0.75,
            mb: showDeltas || showRecon || hasFx ? 0.5 : 0,
          }}
        >
          <Typography
            level="body-xs"
            sx={{ color: "rgba(0,0,0,0.35)", fontWeight: 600, mb: 0.25 }}
          >
            Valuation
          </Typography>
          <Box
            sx={{ display: "flex", justifyContent: "space-between", py: 0.15 }}
          >
            <Typography level="body-xs" sx={{ color: "rgba(0,0,0,0.5)" }}>
              Unit price
            </Typography>
            <Typography
              level="body-xs"
              sx={{
                color: "#1e293b",
                fontWeight: 600,
                fontVariantNumeric: "tabular-nums",
              }}
            >
              {point.unitPrice.toLocaleString(undefined, {
                maximumFractionDigits: 4,
              })}{" "}
              {point.nativeCurrency ?? effectiveCurrency}
            </Typography>
          </Box>
          {point.valueDate && (
            <Box
              sx={{ display: "flex", justifyContent: "space-between", py: 0.15 }}
            >
              <Typography level="body-xs" sx={{ color: "rgba(0,0,0,0.5)" }}>
                Value date
              </Typography>
              <Typography
                level="body-xs"
                sx={{
                  color: "rgba(0,0,0,0.5)",
                  fontVariantNumeric: "tabular-nums",
                }}
              >
                {point.valueDate}
              </Typography>
            </Box>
          )}
        </Box>
      )}

      {/* FX conversion info */}
      {hasFx && point.fxConversion && (
        <Box
          sx={{
            borderTop: "1px solid rgba(0,0,0,0.08)",
            pt: 0.75,
            mb: showDeltas || showRecon ? 0.5 : 0,
          }}
        >
          <Typography
            level="body-xs"
            sx={{ color: "rgba(0,0,0,0.35)", fontWeight: 600, mb: 0.25 }}
          >
            FX Conversion
          </Typography>
          <Box
            sx={{ display: "flex", justifyContent: "space-between", py: 0.15 }}
          >
            <Typography level="body-xs" sx={{ color: "rgba(0,0,0,0.5)" }}>
              Rate
            </Typography>
            <Typography
              level="body-xs"
              sx={{
                color: "#1e293b",
                fontWeight: 600,
                fontVariantNumeric: "tabular-nums",
              }}
            >
              1 {point.fxConversion.fromCurrency} ={" "}
              {point.fxConversion.rate.toLocaleString(undefined, {
                maximumFractionDigits: 6,
              })}{" "}
              {point.fxConversion.toCurrency}
            </Typography>
          </Box>
          <Box
            sx={{ display: "flex", justifyContent: "space-between", py: 0.15 }}
          >
            <Typography level="body-xs" sx={{ color: "rgba(0,0,0,0.5)" }}>
              Rate date
            </Typography>
            <Typography
              level="body-xs"
              sx={{
                color: "rgba(0,0,0,0.5)",
                fontVariantNumeric: "tabular-nums",
              }}
            >
              {point.fxConversion.rateDate}
            </Typography>
          </Box>
        </Box>
      )}

      {/* Delta rows */}
      {showDeltas && deltaRows.length > 0 && (
        <>
          <Box
            sx={{ borderTop: "1px solid rgba(0,0,0,0.08)", pt: 0.75, mb: 0.5 }}
          >
            <Box
              sx={{
                display: "flex",
                justifyContent: "space-between",
                mb: 0.25,
              }}
            >
              <Typography
                level="body-xs"
                sx={{ color: "rgba(0,0,0,0.35)", fontWeight: 600 }}
              >
                Deltas (units)
              </Typography>
            </Box>
          </Box>
          {deltaRows.map((row) => (
            <Box
              key={row.label}
              sx={{
                display: "flex",
                justifyContent: "space-between",
                py: 0.15,
              }}
            >
              <Typography level="body-xs" sx={{ color: "rgba(0,0,0,0.5)" }}>
                {row.label}
              </Typography>
              <Typography
                level="body-xs"
                sx={{
                  minWidth: 70,
                  textAlign: "right",
                  fontWeight: 600,
                  ...deltaStyle(row.amountDelta),
                }}
              >
                {fmtDelta(row.amountDelta)}
              </Typography>
            </Box>
          ))}
        </>
      )}

      {/* Reconciliation — show new values instead of deltas */}
      {showRecon && (
        <Box
          sx={{
            borderTop: "1px solid rgba(0,0,0,0.08)",
            pt: 0.75,
            mt: showDeltas ? 0.5 : 0,
          }}
        >
          <Typography
            level="body-xs"
            sx={{ color: "rgba(0,0,0,0.35)", fontWeight: 600, mb: 0.25 }}
          >
            Reconciliation
          </Typography>
          {point.amountDeltaReconciliation !== 0 && (
            <Box
              sx={{
                display: "flex",
                justifyContent: "space-between",
                py: 0.15,
              }}
            >
              <Typography level="body-xs" sx={{ color: "rgba(0,0,0,0.5)" }}>
                Amount
              </Typography>
              <Typography
                level="body-xs"
                sx={{
                  fontWeight: 600,
                  ...deltaStyle(point.amountDeltaReconciliation),
                }}
              >
                {fmtDelta(point.amountDeltaReconciliation)}
              </Typography>
            </Box>
          )}
        </Box>
      )}
    </Box>
  );
}

interface AssetChartProps {
  assetId: string;
  assetName: string;
  currency: string;
  staleAfter: number; // epoch millis, 0 = not stale
  aggregatedThrough: string | null; // ISO date or null if never aggregated
  displayCurrency?: string;
}

type DateRange = "7d" | "30d" | "90d" | "1y" | "all";

// Must match LineChart's internal default margins
const CHART_MARGIN = { left: 60, right: 10, top: 10, bottom: 30 };

const DATE_RANGES: { label: string; value: DateRange; days?: number }[] = [
  { label: "7D", value: "7d", days: 7 },
  { label: "30D", value: "30d", days: 30 },
  { label: "90D", value: "90d", days: 90 },
  { label: "1Y", value: "1y", days: 365 },
  { label: "All", value: "all" },
];

/**
 * Fill in missing dates across [startIso, endIso]. Missing days carry forward
 * the last known value with all deltas = 0.
 */
function fillDateRange(
  data: AssetAggregation[],
  startIso: string,
  endIso: string,
): AssetAggregation[] {
  const byDate = new Map<string, AssetAggregation>();
  for (const d of data) {
    byDate.set(d.date.slice(0, 10), d);
  }

  const result: AssetAggregation[] = [];
  const cursor = new Date(startIso + "T00:00:00Z");
  const end = new Date(endIso + "T00:00:00Z");
  // Initialize carry-forward values to zero; only fill forward after the first real data point
  let lastDisplayValue = 0;
  let lastNativeValue = 0;
  let lastAmount = 0;
  let lastNativeCurrency: string | null = null;
  let lastDisplayCurrency: string | null = null;
  let lastFxConversion: AssetAggregation["fxConversion"] = null;
  let lastUnitPrice: number | null = null;
  let lastValueDate: string | null = null;
  let hasSeenData = false;

  while (cursor <= end) {
    const key = cursor.toISOString().slice(0, 10);
    const existing = byDate.get(key);
    if (existing) {
      hasSeenData = true;
      lastDisplayValue = getDisplayValue(existing);
      lastNativeValue = existing.nativeValue;
      lastAmount = existing.amount;
      lastNativeCurrency = existing.nativeCurrency;
      lastDisplayCurrency = existing.displayCurrency;
      lastFxConversion = existing.fxConversion;
      lastUnitPrice = existing.unitPrice;
      lastValueDate = existing.valueDate;
      result.push(existing);
    } else if (hasSeenData) {
      result.push({
        assetId: data[0]?.assetId ?? "",
        date: cursor.toISOString(),
        amount: lastAmount,
        amountDeltaTrades: 0,
        amountDeltaReconciliation: 0,
        amountDeltaOther: 0,
        nativeValue: lastNativeValue,
        displayValue: lastDisplayValue,
        nativeCurrency: lastNativeCurrency,
        displayCurrency: lastDisplayCurrency,
        fxConversion: lastFxConversion,
        unitPrice: lastUnitPrice,
        valueDate: lastValueDate,
      });
    }
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return result;
}

const CHART_HEIGHT = 300;

export const AssetChart = ({
  assetId,
  assetName,
  currency,
  staleAfter: initialStaleAfter,
  aggregatedThrough: initialAggregatedThrough,
  displayCurrency: displayCurrencyOverride,
}: AssetChartProps) => {
  const [data, setData] = useState<AssetAggregation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [range, setRange] = useState<DateRange>("30d");
  const [staleAfter, setStaleAfter] = useState(initialStaleAfter);
  const [aggregatedThrough, setAggregatedThrough] = useState(initialAggregatedThrough);
  const containerRef = useRef<HTMLDivElement>(null);

  // Tooltip tracking state
  const [tooltipIndex, setTooltipIndex] = useState<number | null>(null);
  const [mousePos, setMousePos] = useState<{ x: number; y: number }>({
    x: 0,
    y: 0,
  });

  // Keep staleAfter/aggregatedThrough in sync when props change
  useEffect(() => {
    setStaleAfter(initialStaleAfter);
  }, [initialStaleAfter]);
  useEffect(() => {
    setAggregatedThrough(initialAggregatedThrough);
  }, [initialAggregatedThrough]);

  // Compute the current chart date range (null for "all")
  const chartRange = useMemo(() => {
    if (range === "all") return null;
    const days = DATE_RANGES.find((r) => r.value === range)?.days ?? 30;
    const start = daysAgo(days);
    const end = today();
    return { start, end };
  }, [range]);

  // Asset is stale if it has never been aggregated or has a non-zero staleAfter marker.
  // Even transactions before the visible chart range affect holdings/values within the range.
  const isStale = aggregatedThrough === null || staleAfter !== 0;

  // Fetch aggregation data
  const loadData = useCallback(() => {
    if (!assetId) return;
    setLoading(true);
    setError(null);

    const promise = chartRange
      ? fetchAggregation(
          assetId,
          chartRange.start,
          chartRange.end,
          displayCurrencyOverride,
        )
      : fetchAllAggregations(assetId, displayCurrencyOverride);

    promise
      .then((d) => {
        const sorted = [...d].sort(
          (a, b) => new Date(a.date).getTime() - new Date(b.date).getTime(),
        );
        if (chartRange) {
          setData(fillDateRange(sorted, chartRange.start, chartRange.end));
        } else if (sorted.length > 0) {
          // For "all", derive the date range from the data itself
          const startIso = sorted[0].date.slice(0, 10);
          const endIso = sorted[sorted.length - 1].date.slice(0, 10);
          setData(fillDateRange(sorted, startIso, endIso));
        } else {
          setData([]);
        }
      })
      .catch((e) =>
        setError(e instanceof Error ? e.message : "Failed to fetch data"),
      )
      .finally(() => setLoading(false));
  }, [assetId, chartRange, displayCurrencyOverride]);

  // Load data on mount and when range/asset changes
  useEffect(() => {
    loadData();
  }, [loadData]);

  // Poll for staleness resolution when stale
  useEffect(() => {
    if (!isStale || !assetId) return;

    const interval = setInterval(async () => {
      try {
        const res = await fetchAssetStaleness(assetId);
        if (res.staleAfter === 0 && res.aggregatedThrough !== null) {
          setStaleAfter(0);
          setAggregatedThrough(res.aggregatedThrough);
          // Staleness cleared — refresh the chart data
          loadData();
        } else {
          setStaleAfter(res.staleAfter);
          setAggregatedThrough(res.aggregatedThrough);
        }
      } catch {
        // Silently ignore polling errors
      }
    }, 2000);

    return () => clearInterval(interval);
  }, [isStale, assetId, loadData]);

  const currentValue =
    data.length > 0 ? getDisplayValue(data[data.length - 1]) : null;
  const firstValue = data.length > 0 ? getDisplayValue(data[0]) : null;
  const effectiveDisplayCurrency =
    data.length > 0 ? (data[0].displayCurrency ?? currency) : currency;
  const change =
    currentValue !== null && firstValue !== null && firstValue !== 0
      ? ((currentValue - firstValue) / firstValue) * 100
      : null;
  const isPositive = change !== null && change >= 0;

  const xValues = useMemo(
    () => data.map((d) => new Date(d.date).getTime()),
    [data],
  );
  const yValues = useMemo(() => data.map((d) => getDisplayValue(d)), [data]);

  // ── Tooltip mouse handlers ──────────────────────────────────────────────────

  /**
   * Extract per-data-point X pixel positions from the SVG line path rendered by
   * MUI X Charts. We parse the path `d` attribute to pull X from each M/L/C
   * command, giving us exact positions that match the chart's internal scale.
   */
  const getLinePointXPositions = useCallback((): number[] | null => {
    const container = containerRef.current;
    if (!container) return null;
    const path = container.querySelector<SVGPathElement>(
      ".MuiLineElement-root",
    );
    if (!path) return null;
    const d = path.getAttribute("d");
    if (!d) return null;

    // Parse SVG path: extract X from M/L commands and endpoint X from C commands.
    // MUI X Charts v8 uses cubic bezier curves (C commands) with the endpoint
    // being the last pair of coordinates: C cx1,cy1 cx2,cy2 x,y
    const positions: number[] = [];
    // Match M or L followed by x,y  OR  C followed by cx1,cy1 cx2,cy2 x,y
    const re =
      /([MLC])\s*([-\d.e]+)[,\s]([-\d.e]+)(?:[,\s]([-\d.e]+)[,\s]([-\d.e]+)[,\s]([-\d.e]+)[,\s]([-\d.e]+))?/gi;
    let match: RegExpExecArray | null;
    while ((match = re.exec(d)) !== null) {
      const cmd = match[1].toUpperCase();
      if (cmd === "M" || cmd === "L") {
        positions.push(parseFloat(match[2]));
      } else if (cmd === "C") {
        // For cubic bezier, the endpoint x is the 5th capture (match[6])
        positions.push(parseFloat(match[6]));
      }
    }
    return positions.length > 0 ? positions : null;
  }, [data]);

  const handleMouseMove = useCallback(
    (e: React.MouseEvent<HTMLDivElement>) => {
      const container = containerRef.current;
      if (!container || data.length === 0) return;

      const rect = container.getBoundingClientRect();
      const mouseX = e.clientX - rect.left;
      const mouseY = e.clientY - rect.top;

      const xPositions = getLinePointXPositions();
      if (!xPositions || xPositions.length === 0) {
        setTooltipIndex(null);
        return;
      }

      // Check if cursor is within the plot area (between first and last point, with small padding)
      const firstX = xPositions[0];
      const lastX = xPositions[xPositions.length - 1];
      const padding = 5;
      if (mouseX < firstX - padding || mouseX > lastX + padding) {
        setTooltipIndex(null);
        return;
      }

      // Find the closest data point by X position
      let closestIdx = 0;
      let closestDist = Math.abs(mouseX - xPositions[0]);
      for (let i = 1; i < xPositions.length; i++) {
        const dist = Math.abs(mouseX - xPositions[i]);
        if (dist < closestDist) {
          closestDist = dist;
          closestIdx = i;
        }
      }

      setTooltipIndex(closestIdx);
      setMousePos({ x: mouseX, y: mouseY });
    },
    [data, getLinePointXPositions],
  );

  const handleMouseLeave = useCallback(() => {
    setTooltipIndex(null);
  }, []);

  // Compute the X pixel position for the crosshair from the SVG path
  const crosshairX = useMemo(() => {
    if (tooltipIndex === null || data.length === 0) return null;
    const xPositions = getLinePointXPositions();
    if (!xPositions || tooltipIndex >= xPositions.length) return null;
    return xPositions[tooltipIndex];
  }, [tooltipIndex, data, getLinePointXPositions]);

  return (
    <Card
      variant="outlined"
      sx={{ borderRadius: "16px" }}
    >
      <CardContent>
        {/* Header */}
        <Box
          sx={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "flex-start",
            mb: 2,
          }}
        >
          <Box>
            <Typography
              level="title-lg"
              sx={{ fontWeight: 700 }}
            >
              {assetName}
            </Typography>
            <Box
              sx={{ display: "flex", alignItems: "center", gap: 1, mt: 0.5 }}
            >
              {currentValue !== null && (
                <Typography level="h3" sx={{ fontWeight: 800 }}>
                  {currentValue.toLocaleString(undefined, {
                    maximumFractionDigits: 2,
                  })}
                  <Typography
                    component="span"
                    level="body-sm"
                    sx={{ color: "neutral.500", ml: 0.5 }}
                  >
                    {effectiveDisplayCurrency}
                  </Typography>
                </Typography>
              )}
              {change !== null && (
                <Chip
                  size="sm"
                  variant="soft"
                  color={isPositive ? "success" : "danger"}
                  sx={{ fontWeight: 600 }}
                >
                  {isPositive ? "+" : ""}
                  {change.toFixed(2)}%
                </Chip>
              )}
            </Box>
          </Box>

          {/* Range selector */}
          <Box sx={{ display: "flex", gap: 0.5 }}>
            {DATE_RANGES.map((r) => (
              <Box
                key={r.value}
                onClick={() => setRange(r.value)}
                sx={{
                  px: 1.5,
                  py: 0.5,
                  borderRadius: "8px",
                  cursor: "pointer",
                  fontSize: "12px",
                  fontWeight: 600,
                  transition: "all 0.15s",
                  background:
                    range === r.value ? "primary.100" : "transparent",
                  color:
                    range === r.value ? "primary.700" : "neutral.500",
                  "&:hover": {
                    background: "primary.50",
                    color: "primary.700",
                  },
                }}
              >
                {r.label}
              </Box>
            ))}
          </Box>
        </Box>

        {/* Chart body */}
        {loading ? (
          <Box
            sx={{
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
              height: CHART_HEIGHT,
            }}
          >
            <CircularProgress size="md" />
          </Box>
        ) : error ? (
          <Alert color="danger" sx={{ borderRadius: "10px" }}>
            {error}
          </Alert>
        ) : data.length === 0 ? (
          <Box
            sx={{
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
              height: CHART_HEIGHT,
            }}
          >
            <Typography level="body-sm" sx={{ color: "neutral.500" }}>
              No aggregation data yet. Add transactions to generate data.
            </Typography>
          </Box>
        ) : (
          <Box
            ref={containerRef}
            onMouseMove={handleMouseMove}
            onMouseLeave={handleMouseLeave}
            sx={{ position: "relative", cursor: isStale ? "default" : "crosshair" }}
          >
            {/* Stale data overlay */}
            {isStale && (
              <Box
                sx={{
                  position: "absolute",
                  inset: 0,
                  zIndex: 10,
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  justifyContent: "center",
                  background: "rgba(255, 255, 255, 0.85)",
                  backdropFilter: "blur(4px)",
                  borderRadius: "8px",
                  gap: 1.5,
                }}
              >
                <CircularProgress
                  size="md"
                  sx={{
                    "--CircularProgress-trackColor": "var(--joy-palette-primary-100)",
                  }}
                />
                <Typography
                  level="body-sm"
                  sx={{ color: "neutral.700", fontWeight: 600 }}
                >
                  Recalculating…
                </Typography>
                <Typography
                  level="body-xs"
                  sx={{
                    color: "neutral.500",
                    textAlign: "center",
                    maxWidth: 260,
                  }}
                >
                  New transaction data is being processed. The chart will update
                  automatically.
                </Typography>
              </Box>
            )}

            {/* Vertical crosshair line */}
            {!isStale && tooltipIndex !== null && crosshairX !== null && (
              <Box
                sx={{
                  position: "absolute",
                  top: CHART_MARGIN.top,
                  bottom: CHART_MARGIN.bottom,
                  left: crosshairX,
                  width: "1px",
                  background: "rgba(0,0,0,0.2)",
                  pointerEvents: "none",
                  zIndex: 15,
                }}
              />
            )}

            {/* Custom tooltip */}
            {!isStale && tooltipIndex !== null && data[tooltipIndex] && (
              <ChartTooltip
                point={data[tooltipIndex]}
                currency={currency}
                x={crosshairX ?? mousePos.x}
                y={mousePos.y}
                containerWidth={
                  containerRef.current?.getBoundingClientRect().width ?? 600
                }
              />
            )}

            <LineChart
              height={CHART_HEIGHT}
              series={[
                {
                  data: yValues,
                  label: assetName,
                  color: "var(--joy-palette-primary-500)",
                  showMark: false,
                  area: true,
                },
              ]}
              xAxis={[
                {
                  data: xValues,
                  scaleType: "time",
                  valueFormatter: (v) =>
                    new Date(v).toLocaleDateString("en-US", {
                      month: "short",
                      day: "numeric",
                    }),
                },
              ]}
              yAxis={[{ width: 60 }]}
              slotProps={{
                tooltip: { trigger: "none" },
                axisHighlight: { x: "none", y: "none" },
              }}
              sx={{
                "& .MuiLineElement-root": { strokeWidth: 2 },
                "& .MuiAreaElement-root": { fillOpacity: 0.12 },
                "& .MuiChartsAxis-line, & .MuiChartsAxis-tick": {
                  stroke: "rgba(0,0,0,0.12)",
                },
                "& .MuiChartsAxis-tickLabel": {
                  fill: "rgba(0,0,0,0.45)",
                  fontSize: "11px",
                },
                "& .MuiChartsAxis-label": { fill: "rgba(0,0,0,0.45)" },
                // Hide any built-in axis highlight lines
                "& .MuiChartsAxisHighlight-root": { display: "none" },
              }}
            />
          </Box>
        )}
      </CardContent>
    </Card>
  );
};
