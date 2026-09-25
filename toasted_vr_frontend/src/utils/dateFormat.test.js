import { formatLocalDate, formatLocalDateTime } from './dateFormat';

describe('formatLocalDate', () => {
  test('shows a UTC instant with the local calendar date of the browser', () => {
    const utcValue = '2026-09-25T02:30:00Z';
    const localDate = new Date(utcValue);
    const expected = new Date(localDate.getFullYear(), localDate.getMonth(), localDate.getDate()).toLocaleDateString();

    expect(formatLocalDate(utcValue)).toBe(expected);
  });

  test('uses the local day, not the UTC day, when they differ', () => {
    // 02:30 UTC es todavía el día anterior en zonas al oeste de UTC (por ejemplo, Colombia).
    const utcValue = '2026-09-25T02:30:00Z';
    const offsetMinutes = new Date(utcValue).getTimezoneOffset();
    const expectedDay = offsetMinutes > 150 ? 24 : 25;

    expect(new Date(utcValue).getDate()).toBe(expectedDay);
    expect(formatLocalDate(utcValue)).toBe(new Date(2026, 8, expectedDay).toLocaleDateString());
  });

  test('shows a dash when there is no valid date', () => {
    expect(formatLocalDate(null)).toBe('—');
    expect(formatLocalDate('')).toBe('—');
    expect(formatLocalDate('not-a-date')).toBe('—');
  });
});

describe('formatLocalDateTime', () => {
  test('shows a UTC instant with the local date and time of the browser', () => {
    const utcValue = '2026-09-25T02:30:00Z';

    expect(formatLocalDateTime(utcValue)).toBe(
      new Date(utcValue).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' })
    );
  });

  test('shows a dash when there is no valid date', () => {
    expect(formatLocalDateTime(undefined)).toBe('—');
    expect(formatLocalDateTime('not-a-date')).toBe('—');
  });
});
