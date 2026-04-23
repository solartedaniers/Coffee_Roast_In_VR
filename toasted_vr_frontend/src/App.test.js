import { render, screen } from '@testing-library/react';
import App from './App';

test('renders registration title', () => {
  render(<App />);
  expect(screen.getByText(/registro de usuarios/i)).toBeInTheDocument();
});
