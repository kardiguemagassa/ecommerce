import Button, { type ButtonProps } from '@mui/material/Button';
import { useRef, type ChangeEvent } from 'react';
import { IMAGE_TYPES } from '../api/images';

type FilePickerButtonProps = Omit<ButtonProps, 'onClick' | 'children'> & {
  label: string;
  onFile: (file: File) => void;
};

/**
 * A real button that opens the file chooser of a hidden input (images only). A <label> styled as a button around the
 * input would nest two interactive elements (axe: nested-interactive) and read the name twice.
 */
export function FilePickerButton({ label, onFile, ...button }: FilePickerButtonProps) {
  const input = useRef<HTMLInputElement>(null);
  const pick = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = ''; // the same file can be chosen again
    if (file) onFile(file);
  };
  return (
    <>
      <Button {...button} onClick={() => input.current?.click()}>
        {label}
      </Button>
      <input ref={input} type="file" hidden accept={IMAGE_TYPES.join(',')} aria-label={label} onChange={pick} />
    </>
  );
}
