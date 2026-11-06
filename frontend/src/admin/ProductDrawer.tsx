import CloseIcon from '@mui/icons-material/Close';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Skeleton from '@mui/material/Skeleton';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent, type RefObject } from 'react';
import { useSaveProduct } from '../api/admin';
import { categoriesQuery, productQuery, type Product } from '../api/catalog';
import { ApiError } from '../api/problem';
import { notify } from '../app/notifications';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { fr } from '../i18n/fr';
import { ImageDropzone } from './ImageDropzone';
import {
  DESCRIPTION_MAX,
  NAME_MAX,
  emptyProduct,
  serverErrors,
  toInput,
  validate,
  valuesOf,
  type ProductErrors,
  type ProductValues,
} from './productForm';

/** `new`: creation; a number: the product to edit; undefined: closed. */
export type Editing = 'new' | number | undefined;

interface ProductDrawerProps {
  editing: Editing;
  onClose: () => void;
  /** A new product was created: the drawer goes on with it (its image can be added now). */
  onCreated: (id: number) => void;
}

/** Side panel of the products table: the form, then the image once the product exists. */
export function ProductDrawer({ editing, onClose, onCreated }: ProductDrawerProps) {
  // Kept up to date by the form: closing with unsaved changes asks first.
  const dirtyRef = useRef(false);
  const [confirming, setConfirming] = useState(false);

  const requestClose = () => {
    if (dirtyRef.current) setConfirming(true);
    else onClose();
  };

  return (
    <Drawer
      anchor="right"
      open={editing !== undefined}
      onClose={requestClose}
      slotProps={{ paper: { sx: { width: { xs: '100%', sm: 480 }, p: { xs: 2.5, sm: 3.5 } }, role: 'dialog', 'aria-labelledby': 'product-drawer-title' } as object }}
    >
      {editing !== undefined && (
        <ProductEditor key={editing} editing={editing} onClose={requestClose} onDone={onClose} onCreated={onCreated} dirtyRef={dirtyRef} />
      )}
      <ConfirmDialog
        open={confirming}
        title={fr.admin.form.discardTitle}
        hint={fr.admin.form.discardHint}
        confirmLabel={fr.admin.form.discard}
        onConfirm={onClose}
        onClose={() => setConfirming(false)}
      />
    </Drawer>
  );
}

interface EditorProps {
  editing: 'new' | number;
  onClose: () => void;
  onDone: () => void;
  onCreated: (id: number) => void;
  dirtyRef: RefObject<boolean>;
}

function ProductEditor({ editing, onClose, onDone, onCreated, dirtyRef }: EditorProps) {
  const isNew = editing === 'new';
  const { data: product } = useQuery({ ...productQuery(isNew ? 0 : editing), enabled: !isNew });

  return (
    <Box sx={{ display: 'grid', gap: 3 }}>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 2 }}>
        <Typography id="product-drawer-title" variant="h2" sx={{ fontSize: 22 }}>
          {isNew ? fr.admin.form.createTitle : fr.admin.form.editTitle(product?.name ?? '')}
        </Typography>
        <IconButton aria-label={fr.admin.form.close} onClick={onClose} edge="end">
          <CloseIcon />
        </IconButton>
      </Box>
      {isNew ? (
        <ProductForm initial={emptyProduct} onCancel={onClose} onSaved={(saved) => onCreated(saved.id!)} dirtyRef={dirtyRef} />
      ) : product ? (
        <>
          <ImageDropzone product={product} />
          <ProductForm product={product} initial={valuesOf(product)} onCancel={onClose} onSaved={onDone} dirtyRef={dirtyRef} />
        </>
      ) : (
        <Skeleton variant="rounded" height={420} />
      )}
      {isNew && (
        <Typography variant="body2" color="text.secondary">
          {fr.admin.form.imageAfterCreate}
        </Typography>
      )}
    </Box>
  );
}

interface FormProps {
  product?: Product;
  initial: ProductValues;
  onCancel: () => void;
  onSaved: (product: Product) => void;
  dirtyRef: RefObject<boolean>;
}

function ProductForm({ product, initial, onCancel, onSaved, dirtyRef }: FormProps) {
  const { data: categories = [] } = useQuery(categoriesQuery());
  const save = useSaveProduct();
  const [values, setValues] = useState(initial);
  const [errors, setErrors] = useState<ProductErrors>({});
  const [touched, setTouched] = useState(false);

  const changed = (Object.keys(initial) as (keyof ProductValues)[]).some((field) => values[field] !== initial[field]);
  useEffect(() => {
    dirtyRef.current = changed && !save.isSuccess;
    return () => {
      dirtyRef.current = false;
    };
  }, [dirtyRef, changed, save.isSuccess]);

  const change = (field: keyof ProductValues) => (event: { target: { value: string } }) => {
    const next = { ...values, [field]: event.target.value };
    setValues(next);
    if (touched) setErrors(validate(next)); // after a first submit, errors follow the typing
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setTouched(true);
    const found = validate(values);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      notify.warning(fr.admin.form.fix);
      return;
    }
    save.mutate(
      { id: product?.id, input: toInput(values) },
      {
        onSuccess: onSaved,
        onError: (error) => {
          if (error instanceof ApiError && error.status === 400) setErrors(serverErrors(error.problem.errors));
        },
      },
    );
  };

  const field = (name: keyof ProductValues) => ({
    name,
    value: values[name],
    onChange: change(name),
    error: Boolean(errors[name]),
    helperText: errors[name] ?? ' ',
    fullWidth: true,
  });

  return (
    <Box component="form" noValidate onSubmit={submit} sx={{ display: 'grid', gap: 1 }}>
      <TextField {...field('name')} label={fr.admin.form.name} required slotProps={{ htmlInput: { maxLength: NAME_MAX + 50 } }} autoFocus={!product} />
      <TextField {...field('description')} label={fr.admin.form.description} multiline minRows={3} slotProps={{ htmlInput: { maxLength: DESCRIPTION_MAX + 50 } }} />
      <TextField {...field('categoryId')} label={fr.admin.form.category} required select>
        {categories.map((category) => (
          <MenuItem key={category.id} value={String(category.id)}>
            {category.name}
          </MenuItem>
        ))}
      </TextField>
      <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
        <TextField {...field('price')} label={fr.admin.form.price} required slotProps={{ htmlInput: { inputMode: 'decimal' } }} />
        <TextField {...field('stock')} label={fr.admin.form.stock} required slotProps={{ htmlInput: { inputMode: 'numeric' } }} />
      </Box>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1.5 }}>
        <Button onClick={onCancel} disabled={save.isPending}>
          {fr.admin.form.cancel}
        </Button>
        <Button type="submit" variant="contained" loading={save.isPending}>
          {product ? fr.admin.form.save : fr.admin.form.create}
        </Button>
      </Box>
    </Box>
  );
}
