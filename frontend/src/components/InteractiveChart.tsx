import { useRef, useState, useMemo, useCallback } from "react";
import useMediaQuery from "@mui/material/useMediaQuery";
import Box from "@mui/joy/Box";
import { LineChart } from "@mui/x-charts/LineChart";

const CHART_HEIGHT = 300;

const CHART_MARGIN = { top: 10, bottom: 25 };

export interface DragInfo {
  startVal: number;
  endVal: number;
  absChange: number;
  pctChange: number | null;
  startDate: string;
  endDate: string;
}

interface InteractiveChartProps {
  xValues: number[];
  yValues: number[];
  costBasisValues?: number[];
  currency: string;
  label: string;
  chartRange: { start: string; end: string } | null;
  disabled?: boolean;
  height?: number;
  renderTooltip?: (
    index: number,
    x: number,
    y: number,
    containerWidth: number,
  ) => React.ReactNode;
  renderOverlay?: () => React.ReactNode;
  onDragChange?: (info: DragInfo | null) => void;
}

export function InteractiveChart({
  xValues,
  yValues,
  costBasisValues,
  currency,
  label,
  chartRange,
  disabled,
  height: heightProp,
  renderTooltip,
  renderOverlay,
  onDragChange,
}: InteractiveChartProps) {
  const isMobile = useMediaQuery("(max-width:600px)");
  const chartHeight = heightProp ?? CHART_HEIGHT;
  const containerRef = useRef<HTMLDivElement>(null);

  // Tooltip tracking state
  const [tooltipIndex, setTooltipIndex] = useState<number | null>(null);
  const [mousePos, setMousePos] = useState({ x: 0, y: 0 });

  // Drag-to-compare state
  const [dragStartIndex, setDragStartIndex] = useState<number | null>(null);
  const [isDragging, setIsDragging] = useState(false);

  // ── X-axis formatting ──────────────────────────────────────────────────────

  const spansMultipleYears = useMemo(() => {
    if (xValues.length < 2) return false;
    return (
      new Date(xValues[0]).getFullYear() !==
      new Date(xValues[xValues.length - 1]).getFullYear()
    );
  }, [xValues]);

  const xAxisFormatter = useMemo(
    () => (v: number) => {
      const d = new Date(v);
      if (spansMultipleYears) {
        const month = d.toLocaleDateString("en-US", { month: "short" });
        const year = String(d.getFullYear()).slice(2);
        return `${month} '${year}`;
      }
      return d.toLocaleDateString("en-US", { month: "short", day: "numeric" });
    },
    [spansMultipleYears],
  );

  // ── SVG path parsing ───────────────────────────────────────────────────────

  const getLinePointXPositions = useCallback((): number[] | null => {
    const container = containerRef.current;
    if (!container) return null;
    const path = container.querySelector<SVGPathElement>(
      ".MuiLineElement-root",
    );
    if (!path) return null;
    const d = path.getAttribute("d");
    if (!d) return null;

    const positions: number[] = [];
    const re =
      /([MLC])\s*([-\d.e]+)[,\s]([-\d.e]+)(?:[,\s]([-\d.e]+)[,\s]([-\d.e]+)[,\s]([-\d.e]+)[,\s]([-\d.e]+))?/gi;
    let match: RegExpExecArray | null;
    while ((match = re.exec(d)) !== null) {
      const cmd = match[1].toUpperCase();
      if (cmd === "M" || cmd === "L") {
        positions.push(parseFloat(match[2]));
      } else if (cmd === "C") {
        positions.push(parseFloat(match[6]));
      }
    }
    return positions.length > 0 ? positions : null;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [xValues]);

  const findClosestIndex = useCallback(
    (clientX: number): number | null => {
      const container = containerRef.current;
      if (!container || xValues.length === 0) return null;
      const rect = container.getBoundingClientRect();
      const mouseX = clientX - rect.left;
      const xPositions = getLinePointXPositions();
      if (!xPositions || xPositions.length === 0) return null;

      const firstX = xPositions[0];
      const lastX = xPositions[xPositions.length - 1];
      const padding = 5;
      if (mouseX < firstX - padding || mouseX > lastX + padding) return null;

      let closestIdx = 0;
      let closestDist = Math.abs(mouseX - xPositions[0]);
      for (let i = 1; i < xPositions.length; i++) {
        const dist = Math.abs(mouseX - xPositions[i]);
        if (dist < closestDist) {
          closestDist = dist;
          closestIdx = i;
        }
      }
      return closestIdx;
    },
    [xValues, getLinePointXPositions],
  );

  // ── Mouse / touch handlers ─────────────────────────────────────────────────

  const handleMouseMove = useCallback(
    (e: React.MouseEvent<HTMLDivElement>) => {
      const container = containerRef.current;
      if (!container || xValues.length === 0) return;
      const rect = container.getBoundingClientRect();
      const idx = findClosestIndex(e.clientX);
      if (idx === null) {
        if (!isDragging) setTooltipIndex(null);
        return;
      }
      setTooltipIndex(idx);
      setMousePos({ x: e.clientX - rect.left, y: e.clientY - rect.top });
    },
    [xValues, findClosestIndex, isDragging],
  );

  const handleMouseDown = useCallback(
    (e: React.MouseEvent<HTMLDivElement>) => {
      const idx = findClosestIndex(e.clientX);
      if (idx !== null) {
        setDragStartIndex(idx);
        setIsDragging(true);
      }
    },
    [findClosestIndex],
  );

  const handleMouseUp = useCallback(() => {
    setDragStartIndex(null);
    setIsDragging(false);
    onDragChange?.(null);
  }, [onDragChange]);

  const handleMouseLeave = useCallback(() => {
    setTooltipIndex(null);
    setDragStartIndex(null);
    setIsDragging(false);
    onDragChange?.(null);
  }, [onDragChange]);

  const handleTouchMove = useCallback(
    (e: React.TouchEvent<HTMLDivElement>) => {
      if (e.touches.length === 0) return;
      const touch = e.touches[0];
      const container = containerRef.current;
      if (!container || xValues.length === 0) return;
      const rect = container.getBoundingClientRect();
      const idx = findClosestIndex(touch.clientX);
      if (idx === null) {
        if (!isDragging) setTooltipIndex(null);
        return;
      }
      setTooltipIndex(idx);
      setMousePos({ x: touch.clientX - rect.left, y: touch.clientY - rect.top });
      if (!isDragging) {
        setDragStartIndex(idx);
        setIsDragging(true);
      }
    },
    [xValues, findClosestIndex, isDragging],
  );

  const handleTouchEnd = useCallback(() => {
    setTooltipIndex(null);
    setDragStartIndex(null);
    setIsDragging(false);
    onDragChange?.(null);
  }, [onDragChange]);

  // ── Computed positions ─────────────────────────────────────────────────────

  const crosshairX = useMemo(() => {
    if (tooltipIndex === null || xValues.length === 0) return null;
    const xPositions = getLinePointXPositions();
    if (!xPositions || tooltipIndex >= xPositions.length) return null;
    return xPositions[tooltipIndex];
  }, [tooltipIndex, xValues, getLinePointXPositions]);

  const dragStartX = useMemo(() => {
    if (dragStartIndex === null || xValues.length === 0) return null;
    const xPositions = getLinePointXPositions();
    if (!xPositions || dragStartIndex >= xPositions.length) return null;
    return xPositions[dragStartIndex];
  }, [dragStartIndex, xValues, getLinePointXPositions]);

  // Report drag info to parent
  const dragInfo = useMemo((): DragInfo | null => {
    if (
      !isDragging ||
      dragStartIndex === null ||
      tooltipIndex === null ||
      yValues.length === 0
    )
      return null;
    const startIdx = Math.min(dragStartIndex, tooltipIndex);
    const endIdx = Math.max(dragStartIndex, tooltipIndex);
    if (startIdx === endIdx) return null;

    const startVal = yValues[startIdx];
    const endVal = yValues[endIdx];
    const absChange = endVal - startVal;
    const pctChange = startVal !== 0 ? (absChange / startVal) * 100 : null;
    const fmtDate = (ts: number) =>
      new Date(ts).toLocaleDateString(undefined, {
        month: "short",
        day: "numeric",
      });
    return {
      startVal,
      endVal,
      absChange,
      pctChange,
      startDate: fmtDate(xValues[startIdx]),
      endDate: fmtDate(xValues[endIdx]),
    };
  }, [isDragging, dragStartIndex, tooltipIndex, xValues, yValues]);

  // Notify parent of drag changes
  const prevDragRef = useRef<DragInfo | null>(null);
  if (dragInfo !== prevDragRef.current) {
    prevDragRef.current = dragInfo;
    // Schedule callback after render to avoid setState-during-render
    Promise.resolve().then(() => onDragChange?.(dragInfo));
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <Box
      ref={containerRef}
      onMouseMove={handleMouseMove}
      onMouseDown={handleMouseDown}
      onMouseUp={handleMouseUp}
      onMouseLeave={handleMouseLeave}
      onTouchMove={handleTouchMove}
      onTouchEnd={handleTouchEnd}
      sx={{
        position: "relative",
        cursor: disabled ? "default" : "crosshair",
        touchAction: "none",
      }}
    >
      {/* Custom overlay (e.g. staleness) */}
      {renderOverlay?.()}

      {/* Crosshair line */}
      {!disabled && tooltipIndex !== null && crosshairX !== null && (
        <Box
          sx={{
            position: "absolute",
            top: CHART_MARGIN.top,
            bottom: CHART_MARGIN.bottom,
            left: crosshairX,
            width: "1px",
            background: "rgba(0,0,0,0.15)",
            pointerEvents: "none",
            zIndex: 15,
          }}
        />
      )}

      {/* Drag start crosshair */}
      {!disabled && isDragging && dragStartX !== null && (
        <Box
          sx={{
            position: "absolute",
            top: CHART_MARGIN.top,
            bottom: CHART_MARGIN.bottom,
            left: dragStartX,
            width: "1px",
            background: "rgba(0,0,0,0.15)",
            pointerEvents: "none",
            zIndex: 15,
          }}
        />
      )}

      {/* Drag selection highlight */}
      {!disabled &&
        isDragging &&
        dragStartX !== null &&
        crosshairX !== null &&
        dragStartX !== crosshairX && (
          <Box
            sx={{
              position: "absolute",
              top: CHART_MARGIN.top,
              bottom: CHART_MARGIN.bottom,
              left: Math.min(dragStartX, crosshairX),
              width: Math.abs(crosshairX - dragStartX),
              background: "rgba(59, 130, 246, 0.1)",
              pointerEvents: "none",
              zIndex: 14,
            }}
          />
        )}

      {/* Custom tooltip (hidden during drag) */}
      {!disabled &&
        !isDragging &&
        tooltipIndex !== null &&
        renderTooltip?.(
          tooltipIndex,
          crosshairX ?? mousePos.x,
          mousePos.y,
          containerRef.current?.getBoundingClientRect().width ?? 600,
        )}

      <LineChart
        height={chartHeight}
        series={[
          {
            data: yValues,
            label,
            color: "var(--joy-palette-primary-500)",
            showMark: false,
            area: true,
          },
          ...(costBasisValues && costBasisValues.some((v) => v > 0)
            ? [
                {
                  data: costBasisValues,
                  label: "Cost basis",
                  color: "#9e9e9e",
                  showMark: false as const,
                  area: false as const,
                  id: "cost-basis",
                },
              ]
            : []),
        ]}
        xAxis={[
          {
            data: xValues,
            scaleType: "time",
            min: chartRange
              ? new Date(chartRange.start + "T00:00:00Z").getTime()
              : undefined,
            max: chartRange
              ? new Date(chartRange.end + "T00:00:00Z").getTime()
              : undefined,
            valueFormatter: xAxisFormatter,
            tickNumber: isMobile ? 4 : 6,
          },
        ]}
        yAxis={[
          {
            width: 55,
            valueFormatter: (v) =>
              new Intl.NumberFormat("en-US", {
                style: "currency",
                currency,
                notation: "compact",
                maximumFractionDigits: 0,
              }).format(v),
          },
        ]}
        margin={{ left: 0, right: isMobile ? 15 : 5 }}
        slotProps={{
          tooltip: { trigger: "none" },
          axisHighlight: { x: "none", y: "none" },
        }}
        hideLegend
        sx={{
          "& .MuiLineElement-root": { strokeWidth: 2 },
          "& .MuiLineElement-series-cost-basis": {
            strokeDasharray: "6 4",
            strokeWidth: 1.5,
          },
          "& .MuiAreaElement-root": { fillOpacity: 0.12 },
          "& .MuiChartsAxis-line, & .MuiChartsAxis-tick": {
            stroke: "rgba(0,0,0,0.12)",
          },
          "& .MuiChartsAxis-tickLabel": {
            fill: "rgba(0,0,0,0.45)",
            fontSize: { xs: "10px", sm: "11px" },
          },
          "& .MuiChartsAxis-label": { fill: "rgba(0,0,0,0.45)" },
          "& .MuiChartsAxisHighlight-root": { display: "none" },
        }}
      />
    </Box>
  );
}
