import MaillardStagnationMonitor from './MaillardStagnationMonitor';

test('en Maillard y sin subir suma un segundo; si sube vuelve a cero', () => {
  expect(MaillardStagnationMonitor.computeNextSeconds(10, 180, 180.5, false)).toBe(11);
  expect(MaillardStagnationMonitor.computeNextSeconds(10, 181, 180, false)).toBe(0);
});

test('fuera de Maillard conserva el contador', () => {
  expect(MaillardStagnationMonitor.computeNextSeconds(10, 150, 151, false)).toBe(10);
});

test('después del first crack deja de contar: manda el DTR', () => {
  expect(MaillardStagnationMonitor.computeNextSeconds(10, 198, 199, true)).toBe(10);
});

test('queda horneado al llegar al límite de 30 s', () => {
  expect(MaillardStagnationMonitor.isBaked(29)).toBe(false);
  expect(MaillardStagnationMonitor.isBaked(30)).toBe(true);
});
