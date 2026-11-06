import { useMutation, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { notify } from '../app/notifications';
import { fr } from '../i18n/fr';
import { api } from './client';
import { AVATAR_MAX_BYTES, imageProblem, imageUpload } from './images';
import { unwrap } from './problem';
import { sessionKey, type Session } from './session';

/** Why a chosen file cannot be a profile photo (type, 1 MB), or undefined when it can be sent. */
export const avatarProblem = (file: File) => imageProblem(file, AVATAR_MAX_BYTES);

/** The header shows the photo of the session: updated in place, it changes at once, without asking the API again. */
function showInHeader(queryClient: QueryClient, avatarUrl: string | undefined) {
  queryClient.setQueryData<Session>(sessionKey, (session) => (session ? { ...session, avatarUrl } : session));
}

export function useReplaceAvatar() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => unwrap(api.PUT('/api/me/avatar', imageUpload(file))),
    onSuccess: (customer) => {
      showInHeader(queryClient, customer.avatarUrl);
      notify.success(fr.profile.photoSaved);
    },
  });
}

export function useDeleteAvatar() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => unwrap(api.DELETE('/api/me/avatar')),
    onSuccess: () => {
      showInHeader(queryClient, undefined);
      notify.success(fr.profile.photoDeleted);
    },
  });
}
