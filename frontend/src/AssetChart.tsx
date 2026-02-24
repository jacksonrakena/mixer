import { useEffect, useState, useMemo, useRef, useCallback } from "react";
import Box from "@mui/joy/Box";
import Typography from "@mui/joy/Typography";
import CircularProgress from "@mui/joy/CircularProgress";
import Alert from "@mui/joy/Alert";
import Card from "@mui/joy/Card";
import CardContent from "@mui/joy/CardContent";
import Chip from "@mui/joy/Chip";
import { LineChart } from "@mui/x-charts/LineChart";
import { fetchAggregation, fetchAssetStaleness, daysAgo, today, type AssetAggregation } from "./api";

interface AssetChartProps {
  assetId: string;
  assetName: string;
  currency: string;
  staleAfter: number; // epoch millis, 0 = not stale
}

type DateRange = "7d" | "30d" | "90d" | "1y" | "all";

// Must match LineChart's internal default margins
const CHART_MARGIN = { left: 60, right: 10, top: 10, bottom: 30 };

const DATE_RANGES: { label: string; value: DateRange; days: number }[] = [
  { label: "7D", value: "7d", days: 7 },
  { label: "30D", value: "30d", days: 30 },
  { label: "90D", value: "90d", days: 90 },
  { label: "1Y", value: "1y", days: 365 },
  { label: "All", value: "all", days: 3650 },
];

function deltaColor(value: number): string {
  if (value > 0) return "#34d399";
  if (value < 0) return "#f87171";
  return "rgba(255,255,255,0.4)";
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
  const byDate = new Map<string, AssetAggregation>();
  for (const d of data) {
    byDate.set(d.date.slice(0, 10), d);
  }

  const result: AssetAggregation[] = [];
  const cursor = new Date(startIso + "T00:00:00Z");
  const end = new Date(endIso + "T00:00:00Z");
  let lastValue = data.length > 0 ? data[0].value : 0;

  while (cursor <= end) {
    const key = cursor.toISOString().slice(0, 10);
    const existing = byDate.get(key);
    if (existing) {
      lastValue = existing.value;
      result.push(existing);
    } else {
      result.push({
        assetId: data[0]?.assetId ?? "",
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
}: AssetChartProps) => {
  const [data, setData] = useState<AssetAggregation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [range, setRange] = useState<DateRange>("30d");
  const [staleAfter, setStaleAfter] = useState(initialStaleAfter);
  const containerRef = useRef<HTMLDivElement>(null);

  // Keep staleAfter in sync when the prop changes (e.g. after a new transaction)
  useEffect(() => {
    setStaleAfter(initialStaleAfter);
  }, [initialStaleAfter]);

  // Compute the current chart date range
  const chartRange = useMemo(() => {
    const days = DATE_RANGES.find((r) => r.value === range)?.days ?? 30;
    const start = daysAgo(days);
    const end = today();
    return { start, end };
  }, [range]);

  // Determine if the chart's visible range includes the stale date
  const isStale = useMemo(() => {
    if (staleAfter === 0) return false;
    const staleDate = new Date(staleAfter).toISOString().slice(0, 10);
    return staleDate >= chartRange.start && staleDate <= chartRange.end;
  }, [staleAfter, chartRange]);

  // Fetch aggregation data
  const loadData = useCallback(() => {
    if (!assetId) return;
    setLoading(true);
    setError(null);
    fetchAggregation(assetId, chartRange.start, chartRange.end)
      .then((d) => {
        const sorted = [...d].sort(
          (a, b) => new Date(a.date).getTime() - new Date(b.date).getTime(),
        );
        setData(fillDateRange(sorted, chartRange.start, chartRange.end));
      })
      .catch((e) =>
        setError(e instanceof Error ? e.message : "Failed to fetch data"),
      )
      .finally(() => setLoading(false));
  }, [assetId, chartRange]);

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
        if (res.staleAfter === 0) {
          setStaleAfter(0);
          // Staleness cleared — refresh the chart data
          loadData();
        } else {
          setStaleAfter(res.staleAfter);
        }
      } catch {
        // Silently ignore polling errors
      }
    }, 2000);

    return () => clearInterval(interval);
  }, [isStale, assetId, loadData]);

  const currentValue = data.length > 0 ? data[data.length - 1].value : null;
  const firstValue = data.length > 0 ? data[0].value : null;
  const change =
    currentValue !== null && firstValue !== null && firstValue !== 0
      ? ((currentValue - firstValue) / firstValue) * 100
      : null;
  const isPositive = change !== null && change >= 0;

  const xValues = useMemo(
    () => data.map((d) => new Date(d.date).getTime()),
    [data],
  );
  const yValues = useMemo(() => data.map((d) => d.value), [data]);

  return (
    <Card
      variant="outlined"
      sx={{
        background: "rgba(17,24,39,0.6)",
        border: "1px solid rgba(255,255,255,0.07)",
        borderRadius: "16px",
        backdropFilter: "blur(12px)",
      }}
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
              sx={{ color: "white", fontWeight: 700 }}
            >
              {assetName}
            </Typography>
            <Box
              sx={{ display: "flex", alignItems: "center", gap: 1, mt: 0.5 }}
            >
              {currentValue !== null && (
                <Typography level="h3" sx={{ color: "white", fontWeight: 800 }}>
                  {currentValue.toLocaleString(undefined, {
                    maximumFractionDigits: 4,
                  })}
                  <Typography
                    component="span"
                    level="body-sm"
                    sx={{ color: "neutral.400", ml: 0.5 }}
                  >
                    {currency}
                  </Typography>
                </Typography>
              )}
              {change !== null && (
                <Chip
                  size="sm"
                  sx={{
                    background: isPositive
                      ? "rgba(52,211,153,0.15)"
                      : "rgba(248,113,113,0.15)",
                    color: isPositive ? "#34d399" : "#f87171",
                    border: "none",
                    fontWeight: 600,
                  }}
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
                    range === r.value ? "rgba(99,102,241,0.25)" : "transparent",
                  color:
                    range === r.value ? "#818cf8" : "rgba(255,255,255,0.4)",
                  "&:hover": {
                    background: "rgba(99,102,241,0.15)",
                    color: "#a5b4fc",
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
          <Box ref={containerRef} sx={{ position: "relative" }}>
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
                  background: "rgba(10, 14, 26, 0.7)",
                  backdropFilter: "blur(4px)",
                  borderRadius: "8px",
                  gap: 1.5,
                }}
              >
                <CircularProgress
                  size="md"
                  sx={{ "--CircularProgress-trackColor": "rgba(99,102,241,0.15)" }}
                />
                <Typography
                  level="body-sm"
                  sx={{ color: "neutral.300", fontWeight: 600 }}
                >
                  Recalculating…
                </Typography>
                <Typography
                  level="body-xs"
                  sx={{ color: "neutral.500", textAlign: "center", maxWidth: 260 }}
                >
                  New transaction data is being processed. The chart will update automatically.
                </Typography>
              </Box>
            )}
            <LineChart
              height={CHART_HEIGHT}
              series={[
                {
                  data: yValues,
                  label: assetName,
                  color: "#818cf8",
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
              slotProps={{ tooltip: { trigger: "none" } }}
              sx={{
                "& .MuiLineElement-root": { strokeWidth: 2 },
                "& .MuiAreaElement-root": { fillOpacity: 0.12 },
                "& .MuiChartsAxis-line, & .MuiChartsAxis-tick": {
                  stroke: "rgba(255,255,255,0.1)",
                },
                "& .MuiChartsAxis-tickLabel": {
                  fill: "rgba(255,255,255,0.4)",
                  fontSize: "11px",
                },
                "& .MuiChartsAxis-label": { fill: "rgba(255,255,255,0.4)" },
              }}
            />
          </Box>
        )}
      </CardContent>
    </Card>
  );
};
