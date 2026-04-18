import { render, screen } from '@testing-library/react';
import App from './App';

test('renders registration title', () => {
  render(<App />);
  expect(screen.getByText(/crea tu cuenta y verifica tu correo/i)).toBeInTheDocument();
});
