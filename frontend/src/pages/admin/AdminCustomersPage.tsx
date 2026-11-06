import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';
import { adminSearchFrom, customersQuery } from '../../api/admin';
import { CustomerDrawer } from '../../admin/CustomerDrawer';
import { SearchBox } from '../../admin/SearchBox';
import { TablePager } from '../../admin/TablePager';
import { UserAvatar } from '../../components/UserAvatar';
import { fr } from '../../i18n/fr';

/** `?client=3` opens the customer's panel: shareable with a colleague, closed by the back button. */
const CUSTOMER_PARAM = 'client';

/** Customer support: search by name or e-mail, then a customer's identity and orders. */
export function AdminCustomersPage() {
  const [params, setParams] = useSearchParams();
  const search = adminSearchFrom(params);
  const selected = Number(params.get(CUSTOMER_PARAM)) || undefined;
  const { data, isPlaceholderData } = useQuery(customersQuery(search));

  const select = (id?: number) =>
    setParams((current) => {
      const next = new URLSearchParams(current);
      if (id === undefined) next.delete(CUSTOMER_PARAM);
      else next.set(CUSTOMER_PARAM, String(id));
      return next;
    });

  const customers = data?.content ?? [];
  return (
    <Box sx={{ display: 'grid', gap: 2.5 }}>
      <SearchBox key={search.q} label={fr.admin.customers.search} q={search.q} onSearch={(q) => setParams(q ? { q } : {})} />
      {!data ? (
        <Skeleton variant="rounded" height={360} sx={{ borderRadius: 5 }} />
      ) : customers.length === 0 ? (
        <Typography color="text.secondary">{fr.admin.customers.empty}</Typography>
      ) : (
        <>
          <Paper variant="outlined" sx={{ borderRadius: 5, overflow: 'hidden', opacity: isPlaceholderData ? 0.6 : 1 }}>
            <TableContainer>
              <Table sx={{ minWidth: 560 }} aria-busy={isPlaceholderData}>
                <TableHead>
                  <TableRow>
                    <TableCell>{fr.admin.customers.name}</TableCell>
                    <TableCell>{fr.admin.customers.email}</TableCell>
                    <TableCell align="right" />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {customers.map((customer) => (
                    <TableRow key={customer.id} hover>
                      <TableCell component="th" scope="row">
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                          <UserAvatar name={customer.fullName} photo={customer.avatarUrl} size={32} />
                          <Typography sx={{ fontWeight: 600 }}>{customer.fullName}</Typography>
                        </Box>
                      </TableCell>
                      <TableCell sx={{ overflowWrap: 'anywhere' }}>{customer.email}</TableCell>
                      <TableCell align="right">
                        <Button size="small" aria-label={fr.admin.customers.viewLabel(customer.fullName ?? '')} onClick={() => select(customer.id)}>
                          {fr.admin.customers.view}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
          <TablePager base="/admin/clients" q={search.q} page={search.page} pages={data.page.totalPages ?? 0} total={data.page.totalElements ?? 0} />
        </>
      )}
      <CustomerDrawer customerId={selected} onClose={() => select(undefined)} />
    </Box>
  );
}
