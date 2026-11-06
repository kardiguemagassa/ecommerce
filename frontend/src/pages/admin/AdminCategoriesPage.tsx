import DeleteOutlinedIcon from '@mui/icons-material/DeleteOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Skeleton from '@mui/material/Skeleton';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent, type KeyboardEvent } from 'react';
import { useCreateCategory, useDeleteCategory, useRenameCategory } from '../../api/admin';
import { categoriesQuery, type Category } from '../../api/catalog';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { fr } from '../../i18n/fr';

const NAME_MAX = 100;

/** Categories: add, rename in place (Enter / Escape), delete when empty. The route loader has fetched them. */
export function AdminCategoriesPage() {
  const { data: categories } = useQuery(categoriesQuery());
  const remove = useDeleteCategory();
  const [deleting, setDeleting] = useState<Category>();

  return (
    <Box sx={{ display: 'grid', gap: 2.5, maxWidth: 640 }}>
      <AddCategory />
      {!categories ? (
        <Skeleton variant="rounded" height={240} sx={{ borderRadius: 5 }} />
      ) : categories.length === 0 ? (
        <Typography color="text.secondary">{fr.admin.categories.empty}</Typography>
      ) : (
        <Paper variant="outlined" component="ul" aria-label={fr.admin.categories.list} sx={{ borderRadius: 5, m: 0, p: 0, listStyle: 'none' }}>
          {categories.map((category, index) => (
            <Box component="li" key={category.id}>
              {index > 0 && <Divider />}
              <CategoryRow category={category} onDelete={() => setDeleting(category)} />
            </Box>
          ))}
        </Paper>
      )}
      <ConfirmDialog
        open={deleting !== undefined}
        title={fr.admin.categories.deleteTitle(deleting?.name ?? '')}
        hint={fr.admin.categories.deleteHint}
        confirmLabel={fr.admin.categories.delete}
        onConfirm={() => deleting && remove.mutate(deleting)}
        onClose={() => setDeleting(undefined)}
      />
    </Box>
  );
}

function AddCategory() {
  const create = useCreateCategory();
  const [name, setName] = useState('');
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!name.trim()) return;
    create.mutate(name.trim(), { onSuccess: () => setName('') });
  };
  return (
    <Box component="form" onSubmit={submit} sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
      <TextField
        size="small"
        label={fr.admin.categories.add}
        value={name}
        onChange={(event) => setName(event.target.value)}
        slotProps={{ htmlInput: { maxLength: NAME_MAX } }}
        sx={{ flex: 1 }}
      />
      <Button type="submit" variant="contained" loading={create.isPending} disabled={!name.trim()}>
        {fr.admin.categories.addButton}
      </Button>
    </Box>
  );
}

function CategoryRow({ category, onDelete }: { category: Category; onDelete: () => void }) {
  const rename = useRenameCategory();
  const name = category.name ?? '';
  const [draft, setDraft] = useState<string>(); // defined while renaming

  const save = () => {
    const next = draft?.trim();
    if (!next || next === name) {
      setDraft(undefined);
      return;
    }
    rename.mutate({ id: category.id!, name: next }, { onSuccess: () => setDraft(undefined) });
  };
  const keys = (event: KeyboardEvent) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      save();
    } else if (event.key === 'Escape') {
      event.preventDefault();
      setDraft(undefined);
    }
  };

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 2.5, py: 1.25, minHeight: 60 }}>
      {draft === undefined ? (
        <>
          <Typography sx={{ flex: 1, fontWeight: 600 }}>{name}</Typography>
          <IconButton aria-label={fr.admin.categories.renameLabel(name)} onClick={() => setDraft(name)}>
            <EditOutlinedIcon />
          </IconButton>
          <IconButton aria-label={fr.admin.categories.deleteLabel(name)} color="error" onClick={onDelete}>
            <DeleteOutlinedIcon />
          </IconButton>
        </>
      ) : (
        <>
          <TextField
            size="small"
            autoFocus
            label={fr.admin.categories.newName(name)}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={keys}
            disabled={rename.isPending}
            helperText={fr.admin.categories.keyboardHint}
            slotProps={{ htmlInput: { maxLength: NAME_MAX } }}
            sx={{ flex: 1 }}
          />
          <Button onClick={save} loading={rename.isPending}>
            {fr.admin.categories.save}
          </Button>
          <Button color="inherit" onClick={() => setDraft(undefined)} disabled={rename.isPending}>
            {fr.admin.categories.cancel}
          </Button>
        </>
      )}
    </Box>
  );
}
