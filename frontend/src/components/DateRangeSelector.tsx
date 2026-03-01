import Box from "@mui/joy/Box";

export type DateRange = "7d" | "30d" | "90d" | "1y" | "all";

export const DATE_RANGES: { label: string; value: DateRange; days?: number }[] = [
  { label: "7D", value: "7d", days: 7 },
  { label: "30D", value: "30d", days: 30 },
  { label: "90D", value: "90d", days: 90 },
  { label: "1Y", value: "1y", days: 365 },
  { label: "All", value: "all" },
];

export function DateRangeSelector({
  value,
  onChange,
}: {
  value: DateRange;
  onChange: (range: DateRange) => void;
}) {
  return (
    <Box sx={{ display: "flex", gap: { xs: 0.25, sm: 0.5 } }}>
      {DATE_RANGES.map((r) => (
        <Box
          key={r.value}
          onClick={() => onChange(r.value)}
          sx={{
            px: { xs: 1, sm: 1.5 },
            py: 0.5,
            borderRadius: "8px",
            cursor: "pointer",
            fontSize: { xs: "11px", sm: "12px" },
            fontWeight: 600,
            transition: "all 0.15s",
            bgcolor: value === r.value ? "primary.100" : "transparent",
            color: value === r.value ? "primary.700" : "neutral.500",
            "&:hover": { bgcolor: "primary.50", color: "primary.700" },
          }}
        >
          {r.label}
        </Box>
      ))}
    </Box>
  );
}
