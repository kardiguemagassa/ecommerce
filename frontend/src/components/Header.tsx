import SearchIcon from '@mui/icons-material/Search';
import ShoppingCartOutlinedIcon from '@mui/icons-material/ShoppingCartOutlined';
import ShoppingBagOutlinedIcon from '@mui/icons-material/ShoppingBagOutlined';
import AppBar from '@mui/material/AppBar';
import Badge from '@mui/material/Badge';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import InputBase from '@mui/material/InputBase';
import Link from '@mui/material/Link';
import Skeleton from '@mui/material/Skeleton';
import Toolbar from '@mui/material/Toolbar';
import { useState, type FormEvent } from 'react';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router';
import { useCart } from '../api/cart';
import { hasRole, signIn, useSession } from '../api/session';
import { fr } from '../i18n/fr';
import { AccountMenu } from './AccountMenu';

export function Header() {
  const { data: session, isPending } = useSession();
  const [searchParams] = useSearchParams();
  const isCustomer = hasRole(session, 'CUSTOMER');
  const { data: cart } = useCart(isCustomer);
  const count = cart?.totalQuantity ?? 0;

  return (
    <AppBar position="sticky">
      <Toolbar sx={{ maxWidth: 1240, width: '100%', mx: 'auto', gap: 2, flexWrap: 'wrap', py: 1.5 }}>
        <Link
          component={RouterLink}
          to="/"
          aria-label={fr.header.home}
          underline="none"
          color="inherit"
          sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}
        >
          <Box
            sx={{ width: 36, height: 36, borderRadius: 2.5, bgcolor: 'primary.main', color: 'primary.contrastText', display: 'grid', placeItems: 'center' }}
          >
            <ShoppingBagOutlinedIcon fontSize="small" />
          </Box>
          <Box component="span" sx={{ fontFamily: 'Manrope, sans-serif', fontWeight: 800, fontSize: 20, letterSpacing: '-0.02em' }}>
            {fr.appName}
          </Box>
        </Link>

        {/* Keyed by the searched term: the field follows the URL (back button, "Effacer les filtres"). */}
        <SearchForm key={searchParams.get('q') ?? ''} />

        <Box component="nav" aria-label="Compte" sx={{ display: 'flex', alignItems: 'center', gap: 1, ml: { xs: 'auto', md: 0 } }}>
          {isCustomer && (
            <IconButton component={RouterLink} to="/panier" aria-label={fr.header.cart(count)} color="inherit" sx={{ width: 44, height: 44 }}>
              {/* No content at 0: a hidden "0" would still count as visible text of the link (WCAG 2.5.3, label in name). */}
              <Badge badgeContent={count > 0 ? count : undefined} max={999} color="secondary">
                <ShoppingCartOutlinedIcon />
              </Badge>
            </IconButton>
          )}
          {isPending ? (
            <Skeleton variant="rounded" width={140} height={44} aria-label={fr.access.loading} />
          ) : session?.authenticated ? (
            <AccountMenu session={session} />
          ) : (
            <Button variant="contained" onClick={signIn} sx={{ height: 44 }}>
              {fr.header.signIn}
            </Button>
          )}
        </Box>
      </Toolbar>
    </AppBar>
  );
}

/** Catalogue search: the term goes to the URL (/?q=...), so a search can be bookmarked and shared. */
function SearchForm() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [term, setTerm] = useState(params.get('q') ?? '');

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const q = term.trim();
    navigate(q ? `/?q=${encodeURIComponent(q)}` : '/');
  };

  return (
    <Box
      component="form"
      role="search"
      onSubmit={submit}
      // Phones: a full-width row under the logo and the account; wider screens: between them.
      sx={{ flex: { xs: '1 1 100%', md: '1 1 320px' }, order: { xs: 3, md: 0 }, display: 'flex', minWidth: 0 }}
    >
      <InputBase
        value={term}
        onChange={(event) => setTerm(event.target.value)}
        placeholder={fr.header.searchPlaceholder}
        inputProps={{ 'aria-label': fr.header.searchLabel, type: 'search' }}
        sx={{ flex: 1, height: 44, px: 1.75, border: 1, borderColor: '#CFCCC4', borderRight: 0, borderRadius: '10px 0 0 10px', bgcolor: '#FAFAF8' }}
      />
      <Button type="submit" variant="contained" aria-label={fr.header.search} sx={{ minWidth: 52, height: 44, borderRadius: '0 10px 10px 0' }}>
        <SearchIcon />
      </Button>
    </Box>
  );
}
