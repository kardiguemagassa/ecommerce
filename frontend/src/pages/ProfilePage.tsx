import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import PhotoCameraOutlinedIcon from '@mui/icons-material/PhotoCameraOutlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { visuallyHidden } from '@mui/utils';
import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import { avatarProblem, useDeleteAvatar, useReplaceAvatar } from '../api/avatar';
import { IMAGE_TYPES } from '../api/images';
import { useSession } from '../api/session';
import { notify } from '../app/notifications';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { UserAvatar } from '../components/UserAvatar';
import { fr } from '../i18n/fr';

/** Profile: the photo is managed here; name, e-mail and password belong to the Keycloak account console. */
export function ProfilePage() {
  const { data: session } = useSession(); // signed in: the route guard checked it
  const name = session?.name ?? session?.email ?? '';

  return (
    <Box sx={{ display: 'grid', gap: 3, maxWidth: 760 }}>
      <Typography variant="h1" component="h1">
        {fr.pages.profile}
      </Typography>
      <AvatarSection name={name} photo={session?.avatarUrl} />
      <Paper variant="outlined" component="section" aria-labelledby="profile-identity" sx={{ borderRadius: 5, p: { xs: 2.5, sm: 3.5 }, display: 'grid', gap: 2 }}>
        <Typography id="profile-identity" variant="h2" sx={{ fontSize: 20 }}>
          {fr.profile.identity}
        </Typography>
        <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: 'max-content 1fr', gap: '10px 24px' }}>
          <Box component="dt" sx={{ color: 'text.secondary' }}>{fr.profile.name}</Box>
          <Box component="dd" sx={{ m: 0 }}>{session?.name}</Box>
          <Box component="dt" sx={{ color: 'text.secondary' }}>{fr.profile.email}</Box>
          <Box component="dd" sx={{ m: 0, overflowWrap: 'anywhere' }}>{session?.email}</Box>
        </Box>
        {session?.accountUrl && (
          <Box>
            <Link href={session.accountUrl} target="_blank" rel="noopener noreferrer" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, fontWeight: 600 }}>
              {fr.profile.account}
              <OpenInNewIcon fontSize="small" aria-hidden />
              <Box component="span" sx={visuallyHidden}>
                {fr.profile.newTab}
              </Box>
            </Link>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              {fr.profile.accountHint}
            </Typography>
          </Box>
        )}
      </Paper>
    </Box>
  );
}

/**
 * The photo: a chosen file is checked (type, weight) and previewed before anything is sent; "Enregistrer" sends it.
 * The API crops it to a 256 × 256 square and strips its metadata; the header shows the new photo at once.
 */
function AvatarSection({ name, photo }: { name: string; photo?: string }) {
  const [chosen, choose] = usePreview();
  const [confirming, setConfirming] = useState(false);
  const replace = useReplaceAvatar();
  const remove = useDeleteAvatar();
  const busy = replace.isPending || remove.isPending;

  const pick = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = ''; // the same file can be chosen again after a cancel
    if (!file) return;
    const problem = avatarProblem(file);
    if (problem) notify.error(problem);
    else choose(file);
  };

  return (
    <Paper variant="outlined" component="section" aria-labelledby="profile-photo" sx={{ borderRadius: 5, p: { xs: 2.5, sm: 3.5 }, display: 'flex', flexWrap: 'wrap', gap: 3, alignItems: 'center' }}>
      {chosen ? (
        <Box component="img" src={chosen.url} alt={fr.profile.preview} sx={{ width: 112, height: 112, borderRadius: '50%', objectFit: 'cover' }} />
      ) : (
        <UserAvatar name={name} photo={photo} size={112} />
      )}
      <Box sx={{ flex: '1 1 280px', display: 'grid', gap: 1.5 }}>
        <Typography id="profile-photo" variant="h2" sx={{ fontSize: 20 }}>
          {fr.profile.photo}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {fr.profile.photoHint}
        </Typography>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5 }}>
          {chosen ? (
            <>
              <Button variant="contained" loading={replace.isPending} onClick={() => replace.mutate(chosen.file, { onSuccess: () => choose(undefined) })}>
                {fr.profile.save}
              </Button>
              <Button disabled={replace.isPending} onClick={() => choose(undefined)}>
                {fr.profile.cancel}
              </Button>
            </>
          ) : (
            <>
              <Button component="label" variant="contained" startIcon={<PhotoCameraOutlinedIcon />} disabled={busy}>
                {fr.profile.change}
                <Box component="input" type="file" accept={IMAGE_TYPES.join(',')} onChange={pick} sx={visuallyHidden} />
              </Button>
              {photo && (
                <Button color="error" loading={remove.isPending} disabled={busy} onClick={() => setConfirming(true)}>
                  {fr.profile.delete}
                </Button>
              )}
            </>
          )}
        </Box>
      </Box>
      <ConfirmDialog
        open={confirming}
        title={fr.profile.deleteTitle}
        hint={fr.profile.deleteHint}
        confirmLabel={fr.profile.delete}
        onConfirm={() => remove.mutate()}
        onClose={() => setConfirming(false)}
      />
    </Paper>
  );
}

interface Chosen {
  file: File;
  /** Temporary URL of the local file, for the preview; released when replaced, cleared or left. */
  url: string;
}

/** The file chosen for the photo, with its preview URL. */
function usePreview(): [Chosen | undefined, (file: File | undefined) => void] {
  const [chosen, setChosen] = useState<Chosen>();
  const latest = useRef<Chosen>(undefined);
  useEffect(() => {
    latest.current = chosen;
  }, [chosen]);
  useEffect(
    () => () => {
      if (latest.current) URL.revokeObjectURL(latest.current.url);
    },
    [],
  );

  const choose = (file: File | undefined) => {
    if (chosen) URL.revokeObjectURL(chosen.url);
    setChosen(file && { file, url: URL.createObjectURL(file) });
  };
  return [chosen, choose];
}
