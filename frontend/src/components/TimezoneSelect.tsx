import { useState, useEffect, useMemo } from 'react'
import Autocomplete from '@mui/joy/Autocomplete'
import AutocompleteOption from '@mui/joy/AutocompleteOption'
import ListItemContent from '@mui/joy/ListItemContent'
import Typography from '@mui/joy/Typography'
import Box from '@mui/joy/Box'

interface TimezoneOption {
  id: string
  city: string
  region: string
  offset: string
  time: string
}

function buildTimezoneOptions(): TimezoneOption[] {
  const now = new Date()
  return Intl.supportedValuesOf('timeZone').map((tz) => {
    const parts = tz.split('/')
    const city = (parts[parts.length - 1] ?? tz).replace(/_/g, ' ')
    const region = parts.length > 1 ? parts[0] : 'Other'

    const fmt = new Intl.DateTimeFormat('en-US', {
      timeZone: tz,
      hour: 'numeric',
      minute: '2-digit',
      hour12: true,
    })
    const time = fmt.format(now)

    const offsetFmt = new Intl.DateTimeFormat('en-US', {
      timeZone: tz,
      timeZoneName: 'shortOffset',
    })
    const offsetPart = offsetFmt.formatToParts(now).find((p) => p.type === 'timeZoneName')
    const offset = offsetPart?.value ?? ''

    return { id: tz, city, region, offset, time }
  })
}

interface TimezoneSelectProps {
  value: string
  onChange: (value: string) => void
}

export default function TimezoneSelect({ value, onChange }: TimezoneSelectProps) {
  const [tick, setTick] = useState(0)

  // Refresh times every 30 seconds
  useEffect(() => {
    const interval = setInterval(() => setTick((t) => t + 1), 30_000)
    return () => clearInterval(interval)
  }, [])

  const options = useMemo(() => buildTimezoneOptions(), [tick])

  const grouped = useMemo(() => {
    const sorted = [...options].sort((a, b) => {
      const rc = a.region.localeCompare(b.region)
      return rc !== 0 ? rc : a.city.localeCompare(b.city)
    })
    return sorted
  }, [options])

  const selected = useMemo(() => grouped.find((o) => o.id === value) ?? null, [grouped, value])

  return (
    <Autocomplete
      value={selected}
      onChange={(_, val) => val && onChange(val.id)}
      options={grouped}
      groupBy={(option) => option.region}
      getOptionLabel={(option) => `${option.city} (${option.offset})`}
      isOptionEqualToValue={(opt, val) => opt.id === val.id}
      filterOptions={(opts, state) => {
        const q = state.inputValue.toLowerCase()
        if (!q) return opts
        return opts.filter(
          (o) =>
            o.id.toLowerCase().includes(q) ||
            o.city.toLowerCase().includes(q) ||
            o.region.toLowerCase().includes(q) ||
            o.offset.toLowerCase().includes(q),
        )
      }}
      placeholder="Select timezone…"
      size="sm"
      slotProps={{
        listbox: {
          sx: { maxHeight: 320 },
        },
      }}
      renderOption={(props, option) => (
        <AutocompleteOption {...props} key={option.id}>
          <ListItemContent>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
              <Box>
                <Typography level="body-sm" sx={{ fontWeight: 500 }}>
                  {option.city}
                </Typography>
                <Typography level="body-xs" sx={{ color: 'neutral.500' }}>
                  {option.offset}
                </Typography>
              </Box>
              <Typography
                level="body-xs"
                sx={{
                  color: 'neutral.500',
                  fontFamily: 'monospace',
                  flexShrink: 0,
                  ml: 2,
                }}
              >
                {option.time}
              </Typography>
            </Box>
          </ListItemContent>
        </AutocompleteOption>
      )}
    />
  )
}
