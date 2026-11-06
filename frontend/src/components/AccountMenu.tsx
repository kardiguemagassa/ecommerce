import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import { visuallyHidden } from '@mui/utils';
import { useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { hasRole, signOut, type Session } from '../api/session';
import { notify } from '../app/notifications';
import { fr } from '../i18n/fr';
import { UserAvatar } from './UserAvatar';

/** Photo and name of the signed-in user; the menu leads to the profile, the orders, the admin space, the logout. */
export function AccountMenu({ session }: { session: Session }) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const name = session.name ?? session.email ?? '';
  const close = () => setAnchor(null);

  return (
    <>
      <Button
        aria-haspopup="menu"
        aria-expanded={anchor ? 'true' : undefined}
        aria-controls={anchor ? 'account-menu' : undefined}
        onClick={(event) => setAnchor(event.currentTarget)}
        color="inherit"
        variant="outlined"
        endIcon={<KeyboardArrowDownIcon />}
        sx={{ borderRadius: 6, borderColor: 'divider', pl: 0.5, height: 44 }}
      >
        <UserAvatar name={name} photo={session.avatarUrl} />
        {/*
          Named by its content, not by aria-label: an aria-label must contain the visible text (WCAG 2.5.3, label in
          name), which the initials of the avatar would break. Screen readers read the hidden sentence; the visible name
          is only for the eyes.
        */}
        <Box component="span" sx={visuallyHidden}>
          {fr.header.accountMenu(name)}
        </Box>
        <Box
          component="span"
          aria-hidden
          sx={(theme) => ({
            ml: 1, maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
            [theme.breakpoints.down('sm')]: { display: 'none' }, // phones: the photo alone
          })}
        >
          {name}
        </Box>
      </Button>
      <Menu id="account-menu" anchorEl={anchor} open={anchor !== null} onClose={close}>
        <MenuItem component={RouterLink} to="/profil" onClick={close}>
          {fr.header.profile}
        </MenuItem>
        <MenuItem component={RouterLink} to="/commandes" onClick={close}>
          {fr.header.orders}
        </MenuItem>
        {hasRole(session, 'ADMIN') && (
          <MenuItem component={RouterLink} to="/admin" onClick={close}>
            {fr.header.admin}
          </MenuItem>
        )}
        <Divider />
        <MenuItem
          onClick={() => {
            close();
            signOut().catch(notify.error);
          }}
          sx={{ color: 'error.main' }}
        >
          {fr.header.signOut}
        </MenuItem>
      </Menu>
    </>
  );
}
