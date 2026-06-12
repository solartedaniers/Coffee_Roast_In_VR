import { render, screen } from '@testing-library/react';
import App from './App';

test('renders entry experience title', () => {
  render(<App />);
  expect(screen.getByRole('heading', { name: /simulador vr/i })).toBeInTheDocument();
});
