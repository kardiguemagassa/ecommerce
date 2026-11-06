import { useMutation, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { notify } from '../app/notifications';
import { fr } from '../i18n/fr';
import { api } from './client';
import { unwrap } from './problem';
import { sessionKey, type Session } from './session';

/** What the API accepts for a profile photo; checked here first to spare a useless upload, checked again by the API. */
export const AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;
export const AVATAR_MAX_BYTES = 1024 * 1024;

/** Why a chosen file cannot be a profile photo, or undefined when it can be sent. */
export function avatarProblem(file: File): string | undefined {
  if (!(AVATAR_TYPES as readonly string[]).includes(file.type)) return fr.errors.unsupportedImage;
  if (file.size > AVATAR_MAX_BYTES) return fr.profile.tooLarge;
  return undefined;
}

/** The header shows the photo of the session: updated in place, it changes at once, without asking the API again. */
function showInHeader(queryClient: QueryClient, avatarUrl: string | undefined) {
  queryClient.setQueryData<Session>(sessionKey, (session) => (session ? { ...session, avatarUrl } : session));
}

export function useReplaceAvatar() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) =>
      unwrap(
        api.PUT('/api/me/avatar', {
          // The generated type of a binary part is a string; the browser sends the file as a multipart body.
          body: { file: file as unknown as string },
          bodySerializer: (body) => {
            const form = new FormData();
            form.append('file', body!.file as unknown as File);
            return form;
          },
        }),
      ),
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
