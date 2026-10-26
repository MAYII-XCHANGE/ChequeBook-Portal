import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import EntrustTokenModal from './EntrustTokenModal';

describe('EntrustTokenModal', () => {
  it('uses one centered numeric field and limits the token to ten digits', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <EntrustTokenModal
        isSubmitting={false}
        error=""
        onSubmit={onSubmit}
        onCancel={vi.fn()}
      />,
    );
    const input = screen.getByRole('textbox', { name: 'Entrust token' });

    fireEvent.change(input, { target: { value: '12a3456789012' } });

    expect(input).toHaveValue('1234567890');
    fireEvent.click(screen.getByRole('button', { name: 'Verify OTP' }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('1234567890'));
  });
});
