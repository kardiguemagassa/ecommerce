import VerifiedOutlinedIcon from '@mui/icons-material/VerifiedOutlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Rating from '@mui/material/Rating';
import Skeleton from '@mui/material/Skeleton';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { visuallyHidden } from '@mui/utils';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { myReviewQuery, reviewSummaryQuery, reviewsQuery, useDeleteReview, useSaveReview, type Review } from '../api/reviews';
import { hasRole, signIn, useSession } from '../api/session';
import { notify } from '../app/notifications';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { fr } from '../i18n/fr';
import { formatDate } from '../i18n/format';
import { Stars } from './Stars';

export const REVIEWS_ANCHOR = 'avis';
const COMMENT_MAX = 1000;

/**
 * "Avis clients" of the product page: the rating and its spread over the stars, the reviews newest first ("Voir plus
 * d'avis"), and the form of a customer who bought the product (a confirmed order: a verified purchase). The others
 * read why they cannot write one.
 */
export function ProductReviews({ productId }: { productId: number }) {
  const { data: summary } = useQuery(reviewSummaryQuery(productId));
  const reviews = useInfiniteQuery(reviewsQuery(productId));
  const items = reviews.data?.pages.flatMap((page) => page.content ?? []) ?? [];
  const count = summary?.rating?.count ?? 0;

  return (
    <Box component="section" id={REVIEWS_ANCHOR} aria-labelledby="reviews-title" sx={{ display: 'grid', gap: 3, scrollMarginTop: 96 }}>
      <Typography id="reviews-title" variant="h2" sx={{ fontSize: 24 }}>
        {fr.reviews.title}
      </Typography>
      <Box sx={{ display: 'grid', gap: { xs: 3, md: 5 }, gridTemplateColumns: { xs: '1fr', md: '320px minmax(0, 1fr)' }, alignItems: 'start' }}>
        <Box sx={{ display: 'grid', gap: 3 }}>
          {summary ? <Summary average={summary.rating?.average} count={count} distribution={summary.distribution ?? []} /> : <Skeleton variant="rounded" height={180} />}
          <MyReview productId={productId} />
        </Box>
        <Box sx={{ display: 'grid', gap: 2 }}>
          {reviews.isPending ? (
            <Skeleton variant="rounded" height={120} />
          ) : items.length === 0 ? (
            <Typography color="text.secondary">{fr.reviews.none}</Typography>
          ) : (
            <Box component="ul" sx={{ m: 0, p: 0, listStyle: 'none', display: 'grid', gap: 2 }}>
              {items.map((review) => (
                <ReviewItem key={review.id} review={review} />
              ))}
            </Box>
          )}
          {reviews.hasNextPage && (
            <Button variant="outlined" loading={reviews.isFetchingNextPage} onClick={() => void reviews.fetchNextPage()} sx={{ justifySelf: 'start' }}>
              {fr.reviews.more}
            </Button>
          )}
        </Box>
      </Box>
    </Box>
  );
}

function Summary({ average, count, distribution }: { average?: number; count: number; distribution: { stars?: number; count?: number }[] }) {
  return (
    <Paper variant="outlined" sx={{ borderRadius: 4, p: 2.5, display: 'grid', gap: 1.5 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
        <Typography sx={{ fontFamily: 'Manrope, sans-serif', fontSize: 36, fontWeight: 800, lineHeight: 1 }}>
          {average !== undefined && count > 0 ? fr.reviews.average(average) : '–'}
        </Typography>
        <Box>
          <Stars value={average ?? 0} size={20} />
          <Typography variant="body2" color="text.secondary">
            {fr.reviews.count(count)}
          </Typography>
        </Box>
      </Box>
      <Box component="ul" aria-label={fr.reviews.distribution} sx={{ m: 0, p: 0, listStyle: 'none', display: 'grid', gap: 0.75 }}>
        {distribution.map(({ stars = 0, count: n = 0 }) => (
          <Box component="li" key={stars} sx={{ display: 'grid', gridTemplateColumns: '64px 1fr 32px', alignItems: 'center', gap: 1, fontSize: 14 }}>
            <Box component="span" sx={visuallyHidden}>{fr.reviews.starCount(stars, n)}</Box>
            <Box component="span" aria-hidden>{fr.reviews.starsShort(stars)}</Box>
            <Box aria-hidden sx={{ height: 8, borderRadius: 4, bgcolor: '#ECEAE5', overflow: 'hidden' }}>
              <Box sx={{ height: '100%', width: count ? `${(n / count) * 100}%` : 0, bgcolor: '#B26A00' }} />
            </Box>
            <Box component="span" aria-hidden sx={{ textAlign: 'right', color: 'text.secondary' }}>{n}</Box>
          </Box>
        ))}
      </Box>
    </Paper>
  );
}

function ReviewItem({ review }: { review: Review }) {
  return (
    <Paper component="li" variant="outlined" sx={{ borderRadius: 4, p: 2.5, display: 'grid', gap: 1 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 1.5 }}>
        <Stars value={review.rating ?? 0} />
        <Typography sx={{ fontWeight: 600 }}>{review.author}</Typography>
        <Typography variant="body2" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, color: 'success.main' }}>
          <VerifiedOutlinedIcon fontSize="small" aria-hidden />
          {fr.reviews.verified}
        </Typography>
        <Typography variant="body2" color="text.secondary" component="time" dateTime={review.createdAt} sx={{ ml: 'auto' }}>
          {formatDate(review.createdAt)}
        </Typography>
      </Box>
      {review.comment && <Typography sx={{ whiteSpace: 'pre-line', overflowWrap: 'anywhere' }}>{review.comment}</Typography>}
    </Paper>
  );
}

/** The form of a buyer; a visitor is offered to sign in, a customer who did not buy the product reads why. */
function MyReview({ productId }: { productId: number }) {
  const { data: session } = useSession();
  const customer = hasRole(session, 'CUSTOMER');
  const { data: mine, isPending } = useQuery({ ...myReviewQuery(productId), enabled: customer });

  if (!session?.authenticated) {
    return (
      <Paper variant="outlined" sx={{ borderRadius: 4, p: 2.5, display: 'grid', gap: 1.5, justifyItems: 'start' }}>
        <Typography variant="body2">{fr.reviews.signInHint}</Typography>
        <Button variant="outlined" onClick={signIn}>
          {fr.reviews.signIn}
        </Button>
      </Paper>
    );
  }
  if (!customer) return null;
  if (isPending) return <Skeleton variant="rounded" height={160} />;
  if (!mine?.allowed) {
    return (
      <Typography variant="body2" color="text.secondary">
        {fr.reviews.buyersOnly}
      </Typography>
    );
  }
  return <ReviewForm key={mine.review?.updatedAt ?? 'new'} productId={productId} review={mine.review} hidden={mine.hidden ?? false} />;
}

function ReviewForm({ productId, review, hidden }: { productId: number; review?: Review; hidden: boolean }) {
  const save = useSaveReview(productId);
  const remove = useDeleteReview(productId);
  const [rating, setRating] = useState<number | null>(review?.rating ?? null);
  const [comment, setComment] = useState(review?.comment ?? '');
  const [missing, setMissing] = useState(false);
  const [confirming, setConfirming] = useState(false);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!rating) {
      setMissing(true);
      notify.error(fr.reviews.ratingRequired);
      return;
    }
    save.mutate({ input: { rating, comment: comment.trim() || undefined }, editing: Boolean(review) });
  };

  return (
    <Paper component="form" variant="outlined" noValidate onSubmit={submit} aria-labelledby="my-review-title" sx={{ borderRadius: 4, p: 2.5, display: 'grid', gap: 2 }}>
      <Typography id="my-review-title" variant="h3" sx={{ fontSize: 18 }}>
        {review ? fr.reviews.editTitle : fr.reviews.writeTitle}
      </Typography>
      {hidden && (
        <Typography variant="body2" sx={{ p: 1.5, borderRadius: 2, bgcolor: '#FFF4E5', color: '#663C00' }}>
          {fr.reviews.hiddenByModeration}
        </Typography>
      )}
      <Box component="fieldset" sx={{ border: 0, m: 0, p: 0, display: 'grid', gap: 0.5 }} aria-describedby={missing ? 'my-review-rating-error' : undefined}>
        <Typography component="legend" variant="body2" sx={{ fontWeight: 600, p: 0 }}>
          {fr.reviews.yourRating}
        </Typography>
        <Rating
          name="rating"
          value={rating}
          onChange={(_event, value) => {
            setRating(value);
            setMissing(false);
          }}
          getLabelText={fr.reviews.stars}
          size="large"
          sx={{ color: '#B26A00' }}
        />
        {missing && (
          <Typography id="my-review-rating-error" variant="caption" color="error">
            {fr.reviews.ratingRequired}
          </Typography>
        )}
      </Box>
      <TextField
        label={fr.reviews.comment}
        value={comment}
        onChange={(event) => setComment(event.target.value.slice(0, COMMENT_MAX))}
        multiline
        minRows={3}
        helperText={fr.reviews.commentHint(comment.length, COMMENT_MAX)}
      />
      {review?.id !== undefined && (
        <Typography variant="caption" color="text.secondary">
          {fr.reviews.editedOn(formatDate(review.updatedAt))}
        </Typography>
      )}
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5 }}>
        <Button type="submit" variant="contained" loading={save.isPending}>
          {review ? fr.reviews.update : fr.reviews.publish}
        </Button>
        {review && (
          <Button color="error" loading={remove.isPending} onClick={() => setConfirming(true)}>
            {fr.reviews.delete}
          </Button>
        )}
      </Box>
      <ConfirmDialog
        open={confirming}
        title={fr.reviews.deleteTitle}
        hint={fr.reviews.deleteHint}
        confirmLabel={fr.reviews.delete}
        onConfirm={() => remove.mutate()}
        onClose={() => setConfirming(false)}
      />
    </Paper>
  );
}
