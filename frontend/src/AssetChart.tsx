import { useEffect, useState } from "react";
import { Box, CircularProgress, Alert } from "@mui/material";
import {
  ChartsTooltip,
  ChartsTooltipContainer,
  LineChart,
  useAxesTooltip,
  useAxisTooltip,
  useItemTooltip,
} from "@mui/x-charts";

interface AssetData {
  assetId: string;
  date: string;
  amount: number;
  amountDeltaCapitalGains: number;
  amountDeltaTrades: number;
  amountDeltaReconciliation: number;
  amountDeltaOther: number;
  value: number;
  valueDeltaCapitalGains: number;
  valueDeltaTrades: number;
  valueDeltaReconciliation: number;
  valueDeltaOther: number;
}

interface ChartDataPoint {
  date: string;
  value: number;
  originalData: AssetData;
}

const CustomTooltip = (vdat) => {
  const axis = useAxesTooltip({
    directions: ["x"],
  });
  if (axis == null)
    return <ChartsTooltipContainer>nuffin</ChartsTooltipContainer>;
  //   const data = axis[0];
  const original = axis[0].seriesItems[0].formattedValue
    .originalData as AssetData;
  console.log(original);
  //   return (
  //     <ChartsTooltipContainer>{JSON.stringify(original)}</ChartsTooltipContainer>
  //   );

  const reasons = [];
  if (original.amountDeltaCapitalGains !== 0) {
    reasons.push(
      `Capital Gains: ${original.amountDeltaCapitalGains?.toFixed(2)}`,
    );
  }
  if (original.amountDeltaTrades !== 0) {
    reasons.push(`Trades: ${original.amountDeltaTrades?.toFixed(2)}`);
  }
  if (original.amountDeltaReconciliation !== 0) {
    reasons.push(
      `Reconciliation: ${original.amountDeltaReconciliation?.toFixed(2)}`,
    );
  }
  if (original.amountDeltaOther !== 0) {
    reasons.push(`Other: ${original.amountDeltaOther?.toFixed(2)}`);
  }

  return (
    <ChartsTooltipContainer>
      <Box
        sx={{
          backgroundColor: "rgba(0, 0, 0, 0.9)",
          padding: "8px 12px",
          borderRadius: "4px",
          color: "#fff",
          fontSize: "12px",
        }}
      >
        <div style={{ fontWeight: "bold", marginBottom: "4px" }}>
          {new Date(original.date).toLocaleDateString()}
        </div>
        <div style={{ marginBottom: "4px" }}>
          Value: {original.value?.toFixed(2)}
        </div>
        {reasons.length > 0 ? (
          <div>
            <div style={{ marginBottom: "4px" }}>Reasons:</div>
            {reasons.map((reason, idx) => (
              <div key={idx} style={{ fontSize: "11px" }}>
                • {reason}
              </div>
            ))}
          </div>
        ) : (
          <div>No changes</div>
        )}
      </Box>
    </ChartsTooltipContainer>
  );
};

export const AssetChart = () => {
  const [data, setData] = useState<ChartDataPoint[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        const assetId = "6c942179-c993-4b25-86dd-6346fb0e3005";
        const startDate = "2026-02-01";
        const endDate = "2026-02-21";

        const response = await fetch(
          `http://localhost:8080/agg/asset/${assetId}/${startDate}/${endDate}`,
        );

        if (!response.ok) {
          throw new Error(`API error: ${response.status}`);
        }

        const assetData: AssetData[] = await response.json();

        const chartData: ChartDataPoint[] = assetData.map((item) => ({
          date: item.date,
          value: item.value,
          originalData: item,
        }));

        setData(chartData);
        setError(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Failed to fetch data");
        setData([]);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, []);

  if (loading) {
    return (
      <Box
        display="flex"
        justifyContent="center"
        alignItems="center"
        minHeight="400px"
      >
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return <Alert severity="error">Error loading chart: {error}</Alert>;
  }

  if (data.length === 0) {
    return <Alert severity="info">No data available</Alert>;
  }

  return (
    <Box sx={{ width: "100%", height: "500px", padding: "20px" }}>
      <h2>Asset Value Over Time</h2>
      <LineChart
        // dataset={data}
        dataset={data}
        series={[
          {
            dataKey: "value",
            valueFormatter: (code, context) => {
              return data[context.dataIndex];
            },
            // data: data.map((e) => ({ date: e.date, value: e.value })),
            data: data.map((e) => e.value),
            label: "Asset value2",
          },
        ]}
        // xAxis={[{ scaleType: "point", data: data.map((e) => e.date) }]}
        // yAxis={[
        //   { id: "leftAxisId", width: 50 },
        //   { id: "rightAxisId", position: "right" },
        // ]}
        slots={{ tooltip: CustomTooltip }}
        slotProps={{
          tooltip: {
            trigger: "item",
          },
        }}
      />
    </Box>
  );
};
