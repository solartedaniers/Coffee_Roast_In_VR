import { fireEvent, render, screen } from '@testing-library/react';
import ProgressWindow from './ProgressWindow';

const renderWindow = (onClose = jest.fn()) => {
  const view = render(
    <ProgressWindow title="History" closeLabel="Close" onClose={onClose}>
      <p>content</p>
    </ProgressWindow>
  );
  return { ...view, onClose };
};

describe('ProgressWindow', () => {
  afterEach(() => {
    document.body.style.overflow = '';
  });

  it('closes on Escape', () => {
    const { onClose } = renderWindow();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('ignores other keys', () => {
    const { onClose } = renderWindow();
    fireEvent.keyDown(document, { key: 'Enter' });
    expect(onClose).not.toHaveBeenCalled();
  });

  it('closes on a backdrop click but not on a click inside', () => {
    const { onClose } = renderWindow();
    fireEvent.click(screen.getByText('content'));
    expect(onClose).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('dialog'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('locks page scroll while open and restores it on close', () => {
    document.body.style.overflow = 'auto';
    const { unmount } = renderWindow();
    expect(document.body.style.overflow).toBe('hidden');

    unmount();
    expect(document.body.style.overflow).toBe('auto');
  });

  it('stops listening to Escape once closed', () => {
    const { onClose, unmount } = renderWindow();
    unmount();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).not.toHaveBeenCalled();
  });
});
