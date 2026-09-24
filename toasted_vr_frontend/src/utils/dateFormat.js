// Muestra una fecha de la API (Instant en UTC, con "Z") en el calendario
// local del navegador. Sin valor o con un valor inválido muestra "—".
export const formatLocalDate = (value) => {
  if (!value) {
    return '—';
  }

  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString();
};
