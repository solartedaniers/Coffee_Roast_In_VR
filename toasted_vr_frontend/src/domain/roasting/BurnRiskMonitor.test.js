import BurnRiskMonitor from './BurnRiskMonitor';

test('el segundo crack quema aunque no pase del techo', () => {
  expect(BurnRiskMonitor.isBurned(220, true)).toBe(true);
});

test('sobre 200°C y bajo el techo, sin segundo crack, no quema', () => {
  expect(BurnRiskMonitor.isBurned(212, false)).toBe(false);
  expect(BurnRiskMonitor.isBurned(220.1, false)).toBe(true);
});

test('solo cuenta los segundos seguidos sobre el techo', () => {
  expect(BurnRiskMonitor.computeConsecutiveSecondsOverThreshold(4, 210)).toBe(0);
  expect(BurnRiskMonitor.computeConsecutiveSecondsOverThreshold(4, 221)).toBe(5);
});
