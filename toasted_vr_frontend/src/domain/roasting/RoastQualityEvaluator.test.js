import RoastQualityEvaluator from './RoastQualityEvaluator';

// Regresión: un tueste que se pasa de temperatura y de tiempo, pero sin
// disparar el flag explícito de quemado (BurnRiskMonitor), ya no debe
// quedar forzado a un mínimo de 51% en la rama "Perfecto".
test('un tueste sobrecalentado y sobrextendido que no dispara BURNED puntúa bajo, no 51%', () => {
  const finishedSim = {
    finalTemperature: 212,
    maxConsecutiveBurnSeconds: 10,
    burnedFlag: false,
    firstCrackReached: true,
    firstCrackTimeSeconds: 300,
    roastingElapsedSeconds: 1200,
    chargeTemperature: 190,
    maillardStagnationSeconds: 0,
    maillardStagnationFlag: false,
  };

  const { score, result } = RoastQualityEvaluator.evaluate(finishedSim, 'INTERMEDIATE');

  expect(result).toBe('PERFECT');
  expect(score).toBeLessThan(51);
});

// Cargar el café fuera del rango recomendado (180-200°C) debe penalizar
// el puntaje, incluso cuando el resto del tueste habría sido perfecto.
test('cargar fuera del rango recomendado penaliza el puntaje frente a un tueste idéntico dentro del rango', () => {
  const baseSim = {
    finalTemperature: 205,
    maxConsecutiveBurnSeconds: 0,
    burnedFlag: false,
    firstCrackReached: true,
    firstCrackTimeSeconds: 480,
    roastingElapsedSeconds: 780,
    maillardStagnationSeconds: 0,
    maillardStagnationFlag: false,
  };

  const inRange = RoastQualityEvaluator.evaluate({ ...baseSim, chargeTemperature: 190 }, 'INTERMEDIATE');
  const outOfRange = RoastQualityEvaluator.evaluate({ ...baseSim, chargeTemperature: 220 }, 'INTERMEDIATE');

  expect(outOfRange.score).toBeLessThan(inRange.score);
});

// Tesis: después del first crack manda el DTR; estar sobre 200°C no quema.
const developedSim = {
  finalTemperature: 206,
  peakTemperature: 206,
  maxConsecutiveBurnSeconds: 0,
  burnedFlag: false,
  firstCrackReached: true,
  firstCrackTimeSeconds: 600,
  roastingElapsedSeconds: 760,
  chargeTemperature: 190,
  maillardStagnationSeconds: 0,
  maillardStagnationFlag: false,
};

test('un desarrollo correcto sobre 200°C (DTR 21 %) es PERFECT, no BURNED', () => {
  const { result, score } = RoastQualityEvaluator.evaluate(developedSim, 'ADVANCED');

  expect(result).toBe('PERFECT');
  expect(score).toBeGreaterThan(75);
});

test('pasar del techo de 220°C en algún momento quema aunque la final quede debajo', () => {
  const { result } = RoastQualityEvaluator.evaluate({ ...developedSim, peakTemperature: 220.5 }, 'INTERMEDIATE');

  expect(result).toBe('BURNED');
});

test('justo en el techo de 220°C todavía no quema', () => {
  const { result } = RoastQualityEvaluator.evaluate(
    { ...developedSim, finalTemperature: 220, peakTemperature: 220 },
    'INTERMEDIATE'
  );

  expect(result).toBe('PERFECT');
});

test('el puntaje del quemado baja por cada segundo seguido sobre el techo', () => {
  const burned = { ...developedSim, burnedFlag: true, peakTemperature: 226, finalTemperature: 226 };
  const brief = RoastQualityEvaluator.evaluate({ ...burned, maxConsecutiveBurnSeconds: 0 }, 'INTERMEDIATE');
  const long = RoastQualityEvaluator.evaluate({ ...burned, maxConsecutiveBurnSeconds: 25 }, 'INTERMEDIATE');

  expect(brief).toEqual({ result: 'BURNED', score: 18 });
  expect(long).toEqual({ result: 'BURNED', score: 8 });
});
