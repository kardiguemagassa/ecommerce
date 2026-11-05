import Box from '@mui/material/Box';
import { Outlet } from 'react-router';
import { ErrorSnackbar } from './ErrorSnackbar';
import { Header } from './Header';

export function Layout() {
  return (
    <>
      <Header />
      <Box component="main" sx={{ maxWidth: 1240, mx: 'auto', px: 3, py: 4 }}>
        <Outlet />
      </Box>
      <ErrorSnackbar />
    </>
  );
}
