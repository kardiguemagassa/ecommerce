import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink, useSearchParams } from 'react-router';
import { HIDDEN_ONLY_PARAM, HIDDEN_ONLY_VALUE, adminReviewsQuery, reviewModerationFrom, useSetReviewVisibility } from '../../api/admin';
import { TablePager } from '../../admin/TablePager';
import { fr } from '../../i18n/fr';
import { formatDate } from '../../i18n/format';
import { Stars } from '../../product/Stars';

/**
 * Moderation of the reviews, newest first: an abusive or off-topic one is hidden from the shop (it leaves the rating
 * of its product), or shown again. A review is never edited nor deleted here: its author keeps it.
 */
export function AdminReviewsPage() {
  const [params, setParams] = useSearchParams();
  const search = reviewModerationFrom(params);
  const { data, isPlaceholderData } = useQuery(adminReviewsQuery(search));
  const visibility = useSetReviewVisibility();
  const reviews = data?.content ?? [];
  const filters: Record<string, string> = search.hiddenOnly ? { [HIDDEN_ONLY_PARAM]: HIDDEN_ONLY_VALUE } : {};

  return (
    <Box sx={{ display: 'grid', gap: 2.5 }}>
      <ToggleButtonGroup
        exclusive
        size="small"
        value={search.hiddenOnly ? 'hidden' : 'all'}
        onChange={(_event, value: string | null) => value && setParams(value === 'hidden' ? { [HIDDEN_ONLY_PARAM]: HIDDEN_ONLY_VALUE } : {})}
        aria-label={fr.admin.reviews.filter}
        sx={{ justifySelf: 'start' }}
      >
        <ToggleButton value="all">{fr.admin.reviews.all}</ToggleButton>
        <ToggleButton value="hidden">{fr.admin.reviews.hiddenOnly}</ToggleButton>
      </ToggleButtonGroup>
      {!data ? (
        <Skeleton variant="rounded" height={360} sx={{ borderRadius: 5 }} />
      ) : reviews.length === 0 ? (
        <Typography color="text.secondary">{search.hiddenOnly ? fr.admin.reviews.emptyHidden : fr.admin.reviews.empty}</Typography>
      ) : (
        <>
          <Paper variant="outlined" sx={{ borderRadius: 5, overflow: 'hidden', opacity: isPlaceholderData ? 0.6 : 1 }}>
            <TableContainer>
              <Table sx={{ minWidth: 760 }} aria-busy={isPlaceholderData}>
                <TableHead>
                  <TableRow>
                    <TableCell>{fr.admin.reviews.product}</TableCell>
                    <TableCell>{fr.admin.reviews.author}</TableCell>
                    <TableCell>{fr.admin.reviews.rating}</TableCell>
                    <TableCell>{fr.admin.reviews.comment}</TableCell>
                    <TableCell>{fr.admin.reviews.date}</TableCell>
                    <TableCell>{fr.admin.reviews.status}</TableCell>
                    <TableCell align="right" />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {reviews.map((review) => {
                    const author = review.authorName ?? review.authorEmail ?? '';
                    const product = review.productName ?? '';
                    return (
                      <TableRow key={review.id} hover>
                        <TableCell component="th" scope="row">
                          <Link component={RouterLink} to={`/produits/${review.productId}`} sx={{ fontWeight: 600 }}>
                            {product}
                          </Link>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>{author}</Typography>
                          <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{review.authorEmail}</Typography>
                        </TableCell>
                        <TableCell>
                          <Stars value={review.rating ?? 0} size={16} />
                        </TableCell>
                        <TableCell sx={{ maxWidth: 320 }}>
                          {review.comment ? (
                            <Typography variant="body2" sx={{ display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                              {review.comment}
                            </Typography>
                          ) : (
                            <Typography variant="body2" color="text.secondary">{fr.admin.reviews.noComment}</Typography>
                          )}
                        </TableCell>
                        <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDate(review.createdAt)}</TableCell>
                        <TableCell>
                          <Chip size="small" label={review.hidden ? fr.admin.reviews.hidden : fr.admin.reviews.visible} color={review.hidden ? 'warning' : 'success'} variant="outlined" />
                        </TableCell>
                        <TableCell align="right">
                          <Button
                            size="small"
                            color={review.hidden ? 'primary' : 'error'}
                            aria-label={review.hidden ? fr.admin.reviews.showLabel(author, product) : fr.admin.reviews.hideLabel(author, product)}
                            disabled={visibility.isPending}
                            onClick={() => visibility.mutate({ id: review.id!, hidden: !review.hidden })}
                          >
                            {review.hidden ? fr.admin.reviews.show : fr.admin.reviews.hide}
                          </Button>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
          <TablePager base="/admin/avis" q="" page={search.page} pages={data.page?.totalPages ?? 0} total={data.page?.totalElements ?? 0} filters={filters} />
        </>
      )}
    </Box>
  );
}
