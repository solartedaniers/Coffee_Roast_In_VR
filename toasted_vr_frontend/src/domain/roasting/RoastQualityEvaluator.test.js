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
    maillardStagnationSeconds: 0,
    maillardStagnationFlag: false,
  };

  const { score, result } = RoastQualityEvaluator.evaluate(finishedSim, 'INTERMEDIATE');

  expect(result).toBe('PERFECT');
  expect(score).toBeLessThan(51);
});
