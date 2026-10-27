// Muestra una fecha de la API (Instant en UTC, con "Z") en el calendario
// local del navegador. Sin valor o con un valor inválido muestra "—".
export const formatLocalDate = (value) => {
  if (!value) {
    return '—';
  }

  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString();
};

// Igual que formatLocalDate, con la hora local; sirve para distinguir varias
// sesiones del mismo día en el historial.
export const formatLocalDateTime = (value) => {
  if (!value) {
    return '—';
  }

  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? '—'
    : date.toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' });
};
