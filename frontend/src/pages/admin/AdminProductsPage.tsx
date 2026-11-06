import AddIcon from '@mui/icons-material/Add';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
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
import { useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router';
import { adminProductsQuery, adminSearchFrom, useDeleteProduct } from '../../api/admin';
import type { Product } from '../../api/catalog';
import { ProductDrawer, type Editing } from '../../admin/ProductDrawer';
import { SearchBox } from '../../admin/SearchBox';
import { StockCell } from '../../admin/StockCell';
import { TablePager } from '../../admin/TablePager';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { fr } from '../../i18n/fr';
import { formatPrice } from '../../i18n/format';
import { ProductImage } from '../../product/ProductImage';

/** `?produit=nouveau` or `?produit=12` opens the side panel: shareable, closed by the back button. */
const EDIT_PARAM = 'produit';
const NEW = 'nouveau';

function editingFrom(params: URLSearchParams): Editing {
  const value = params.get(EDIT_PARAM);
  if (value === NEW) return 'new';
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : undefined;
}

/** Products of the shop: search, create, edit (with the image), delete. The route loader has fetched the page. */
export function AdminProductsPage() {
  const [params, setParams] = useSearchParams();
  const search = adminSearchFrom(params);
  const editing = editingFrom(params);
  const { data, isPlaceholderData } = useQuery(adminProductsQuery(search));
  const remove = useDeleteProduct();
  const [deleting, setDeleting] = useState<Product>();

  const edit = (value: Editing) =>
    setParams((current) => {
      const next = new URLSearchParams(current);
      if (value === undefined) next.delete(EDIT_PARAM);
      else next.set(EDIT_PARAM, value === 'new' ? NEW : String(value));
      return next;
    });

  const products = data?.content ?? [];
  return (
    <Box sx={{ display: 'grid', gap: 2.5 }}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'center', justifyContent: 'space-between' }}>
        <SearchBox key={search.q} label={fr.admin.products.search} q={search.q} onSearch={(q) => setParams(q ? { q } : {})} />
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => edit('new')}>
          {fr.admin.products.add}
        </Button>
      </Box>

      {!data ? (
        <Skeleton variant="rounded" height={420} sx={{ borderRadius: 5 }} />
      ) : products.length === 0 ? (
        <Typography color="text.secondary">{fr.admin.products.empty}</Typography>
      ) : (
        <Paper variant="outlined" sx={{ borderRadius: 5, overflow: 'hidden', opacity: isPlaceholderData ? 0.6 : 1 }}>
          <TableContainer>
            <Table sx={{ minWidth: 720 }} aria-busy={isPlaceholderData}>
              <TableHead>
                <TableRow>
                  <TableCell>{fr.admin.products.name}</TableCell>
                  <TableCell>{fr.admin.products.category}</TableCell>
                  <TableCell align="right">{fr.admin.products.price}</TableCell>
                  <TableCell align="right">{fr.admin.products.stock}</TableCell>
                  <TableCell align="right">{fr.admin.products.actions}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {products.map((product) => {
                  const name = product.name ?? '';
                  return (
                    <TableRow key={product.id} hover>
                      <TableCell component="th" scope="row">
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                          <Box sx={{ width: 56, flex: 'none', borderRadius: 1.5, overflow: 'hidden' }}>
                            <ProductImage product={product} decorative />
                          </Box>
                          <Link component={RouterLink} to={`/produits/${product.id}`} underline="hover" color="text.primary" sx={{ fontWeight: 600 }}>
                            {name}
                          </Link>
                        </Box>
                      </TableCell>
                      <TableCell>{product.categoryName}</TableCell>
                      <TableCell align="right">{formatPrice(product.price)}</TableCell>
                      <TableCell align="right">
                        <StockCell stock={product.stock ?? 0} />
                      </TableCell>
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                        <Button size="small" aria-label={fr.admin.products.editLabel(name)} onClick={() => edit(product.id)}>
                          {fr.admin.products.edit}
                        </Button>
                        <Button size="small" color="error" aria-label={fr.admin.products.deleteLabel(name)} onClick={() => setDeleting(product)}>
                          {fr.admin.products.delete}
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      )}
      {data && products.length > 0 && (
        <TablePager base="/admin/produits" q={search.q} page={search.page} pages={data.page.totalPages ?? 0} total={data.page.totalElements ?? 0} />
      )}

      <ProductDrawer editing={editing} onClose={() => edit(undefined)} onCreated={(id) => edit(id)} />
      <ConfirmDialog
        open={deleting !== undefined}
        title={fr.admin.products.deleteTitle(deleting?.name ?? '')}
        hint={fr.admin.products.deleteHint}
        confirmLabel={fr.admin.products.delete}
        onConfirm={() => deleting && remove.mutate(deleting)}
        onClose={() => setDeleting(undefined)}
      />
    </Box>
  );
}
