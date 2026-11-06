import Avatar from '@mui/material/Avatar';
import { initials } from './initials';

/**
 * Profile photo, or the initials on the accent colour when there is none (same size either way). Always shown next to
 * the person's name, so hidden from screen readers: the initials would be read before the name (and break WCAG 2.5.3,
 * label in name, on the account button).
 */
export function UserAvatar({ name, photo, size = 36 }: { name?: string; photo?: string; size?: number }) {
  return (
    <Avatar
      src={photo}
      alt=""
      aria-hidden
      sx={{ width: size, height: size, bgcolor: 'primary.light', color: 'primary.main', fontSize: size * 0.38, fontWeight: 600 }}
    >
      {initials(name)}
    </Avatar>
  );
}
