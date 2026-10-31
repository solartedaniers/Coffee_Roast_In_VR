import ChartSampleSelector from './ChartSampleSelector';
import RoastTraceRecorder from './RoastTraceRecorder';
import { CHART_SAMPLE_INTERVAL_SECONDS, ROAST_TRACE_SAMPLE_INTERVAL_SECONDS } from './RoastConstants';

const perSecond = (seconds) =>
  Array.from({ length: seconds }, (_, index) => ({ time: index + 1, temp: 100 + index, gas: 50 }));

test('la gráfica dibuja un punto cada 2 segundos', () => {
  const times = ChartSampleSelector.select(perSecond(10)).map((sample) => sample.time);

  expect(CHART_SAMPLE_INTERVAL_SECONDS).toBe(2);
  expect(times).toEqual([2, 4, 6, 8, 10]);
});

test('la traza sigue con una muestra cada 5 segundos', () => {
  const sim = { chargeStartElapsedSeconds: 0, roastingElapsedSeconds: 20, samples: perSecond(20), usedAutoMode: false };
  const times = RoastTraceRecorder.build(sim).samples.map((sample) => sample.t);

  expect(ROAST_TRACE_SAMPLE_INTERVAL_SECONDS).toBe(5);
  expect(times).toEqual([1, 5, 10, 15, 20]);
});
