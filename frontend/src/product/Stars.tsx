import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import StarHalfIcon from '@mui/icons-material/StarHalf';
import Box from '@mui/material/Box';
import { fr } from '../i18n/fr';

/** Amber stars, contrast 3:1 against white at least (non-text contrast of WCAG). */
const STAR = '#B26A00';

/**
 * A rating as five stars, read as one image: "Note : 4,3 sur 5". Halves from .25 to .75; `size` in pixels.
 * The stars only repeat the number for the eye: the label carries it for screen readers.
 */
export function Stars({ value, size = 18, label }: { value: number; size?: number; label?: string }) {
  return (
    <Box role="img" aria-label={label ?? fr.reviews.ratingLabel(value)} sx={{ display: 'inline-flex', color: STAR, lineHeight: 0 }}>
      {[1, 2, 3, 4, 5].map((star) => {
        const Icon = value >= star - 0.25 ? StarIcon : value >= star - 0.75 ? StarHalfIcon : StarBorderIcon;
        return <Icon key={star} aria-hidden sx={{ fontSize: size }} />;
      })}
    </Box>
  );
}
