import RoastFlavorProfileDescriber from './RoastFlavorProfileDescriber';

// Regresión: un RAW que sí cruzó first crack antes de que la temperatura
// se desplomara no debe describirse igual que uno que nunca se acercó.
test('RAW con firstCrackReached true usa RAW_REGRESSED, no RAW_SEVERE/RAW_CLOSE', () => {
  const key = RoastFlavorProfileDescriber.describe({
    result: 'RAW',
    finalTemperature: 166,
    firstCrackReached: true,
    roastingElapsedSeconds: 900,
    firstCrackTimeSeconds: 600,
  });

  expect(key).toBe('RAW_REGRESSED');
});

// Sin regresión: un RAW que nunca alcanzó first crack conserva el
// comportamiento previo (severidad según distancia al umbral).
test('RAW sin firstCrackReached conserva RAW_SEVERE/RAW_CLOSE según la distancia', () => {
  const severe = RoastFlavorProfileDescriber.describe({
    result: 'RAW',
    finalTemperature: 150,
    firstCrackReached: false,
    roastingElapsedSeconds: 400,
    firstCrackTimeSeconds: null,
  });
  const close = RoastFlavorProfileDescriber.describe({
    result: 'RAW',
    finalTemperature: 180,
    firstCrackReached: false,
    roastingElapsedSeconds: 400,
    firstCrackTimeSeconds: null,
  });

  expect(severe).toBe('RAW_SEVERE');
  expect(close).toBe('RAW_CLOSE');
});
