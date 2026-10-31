import ProbeTemperatureModel from './ProbeTemperatureModel';
import { PROBE_RESPONSE_TIME_CONSTANT_SEC } from './RoastConstants';

function followFor(seconds, startProbeTemp, beanTemp) {
  let probe = startProbeTemp;
  for (let second = 0; second < seconds; second++) {
    probe = ProbeTemperatureModel.computeNextProbeTemp(probe, beanTemp);
  }
  return probe;
}

test('la sonda se mueve hacia el grano sin pasarse', () => {
  const probe = ProbeTemperatureModel.computeNextProbeTemp(190, 100);

  expect(probe).toBeLessThan(190);
  expect(probe).toBeGreaterThan(100);
});

test('en una constante de tiempo la sonda recorre cerca del 63 % de la diferencia', () => {
  const probe = followFor(PROBE_RESPONSE_TIME_CONSTANT_SEC, 190, 100);
  const covered = (190 - probe) / (190 - 100);

  expect(covered).toBeGreaterThan(0.6);
  expect(covered).toBeLessThan(0.66);
});

test('si el grano no cambia, la sonda termina marcando lo mismo que el grano', () => {
  expect(followFor(10 * PROBE_RESPONSE_TIME_CONSTANT_SEC, 190, 100)).toBeCloseTo(100, 0);
});
