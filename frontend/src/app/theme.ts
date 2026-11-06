import { createTheme } from '@mui/material/styles';

const headings = { fontFamily: 'Manrope, Inter, system-ui, sans-serif', fontWeight: 800, letterSpacing: '-0.02em' };

/** Look of the mockups (v2.0.0 design): petrol accent, warm grey background, Manrope headings, Inter text. */
export const theme = createTheme({
  palette: {
    primary: { main: '#0E5A61', dark: '#08393E', light: '#E3EEEF', contrastText: '#FFFFFF' },
    secondary: { main: '#B4530D', contrastText: '#FFFFFF' },
    error: { main: '#A12C2C' },
    warning: { main: '#8A4B08' },
    success: { main: '#2E6B3A' },
    background: { default: '#F6F5F2', paper: '#FFFFFF' },
    text: { primary: '#1B1D1F', secondary: '#5D615F' },
    divider: '#E4E2DC',
  },
  shape: { borderRadius: 10 },
  typography: {
    fontFamily: 'Inter, system-ui, sans-serif',
    h1: { ...headings, fontSize: '2rem' },
    h2: { ...headings, fontSize: '1.25rem', fontWeight: 700 },
    h3: { ...headings, fontSize: '1.0625rem', fontWeight: 700 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiButton: { defaultProps: { disableElevation: true } },
    MuiAppBar: {
      defaultProps: { elevation: 0, color: 'inherit' },
      styleOverrides: { root: { borderBottom: '1px solid #E4E2DC', backgroundColor: '#FFFFFF' } },
    },
    MuiPaper: { styleOverrides: { outlined: { borderColor: '#E4E2DC' } } },
  },
});
