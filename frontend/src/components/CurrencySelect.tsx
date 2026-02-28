import Box from '@mui/joy/Box'
import Select from '@mui/joy/Select'
import Option from '@mui/joy/Option'
import Typography from '@mui/joy/Typography'
import type { SupportedCurrency } from '../api'

const CURRENCY_NAMES: Record<string, string> = {
  AUD: 'Australian Dollar',
  USD: 'US Dollar',
  NZD: 'New Zealand Dollar',
  EUR: 'Euro',
  GBP: 'British Pound',
  HKD: 'Hong Kong Dollar',
}

interface CurrencySelectProps {
  value: SupportedCurrency
  onChange: (value: SupportedCurrency) => void
  currencies: string[]
  size?: 'sm' | 'md' | 'lg'
}

export default function CurrencySelect({ value, onChange, currencies, size = 'sm' }: CurrencySelectProps) {
  return (
    <Select
      value={value}
      onChange={(_, val) => val && onChange(val as SupportedCurrency)}
      size={size}
      renderValue={(selected) => selected ? (
        <Box sx={{ textAlign: 'left' }}>
          <Typography level="body-sm" sx={{ fontWeight: 600, lineHeight: 1.3 }}>
            {selected.value}
          </Typography>
          <Typography level="body-xs" sx={{ color: 'neutral.500', lineHeight: 1.2 }}>
            {CURRENCY_NAMES[selected.value as string] ?? ''}
          </Typography>
        </Box>
      ) : null}
      sx={{
        fontWeight: 600,
        fontSize: '13px',
        borderRadius: '8px',
        py: 0.75,
        textAlign: 'left',
        '& .MuiSelect-button': { textAlign: 'left' },
      }}
    >
      {currencies.map((cur) => (
        <Option key={cur} value={cur}>
          <Box>
            <Typography level="body-sm" sx={{ fontWeight: 600 }}>{cur}</Typography>
            <Typography level="body-xs" sx={{ color: 'neutral.500' }}>
              {CURRENCY_NAMES[cur] ?? cur}
            </Typography>
          </Box>
        </Option>
      ))}
    </Select>
  )
}
