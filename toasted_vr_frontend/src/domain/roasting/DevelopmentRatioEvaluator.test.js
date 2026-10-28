import DevelopmentRatioEvaluator from './DevelopmentRatioEvaluator';
import KnowledgeLevelRules from './KnowledgeLevelRules';
import { DTR_IDEAL_MIN_RATIO, DTR_IDEAL_MAX_RATIO } from './RoastConstants';

const toleranceOf = (level) => KnowledgeLevelRules.forLevel(level).dtrToleranceRatio;

test('el rango ideal 18-22 % no penaliza en ningún nivel', () => {
  for (const level of ['BEGINNER', 'INTERMEDIATE', 'ADVANCED']) {
    expect(DevelopmentRatioEvaluator.computePenalty(DTR_IDEAL_MIN_RATIO, toleranceOf(level))).toBe(0);
    expect(DevelopmentRatioEvaluator.computePenalty(DTR_IDEAL_MAX_RATIO, toleranceOf(level))).toBe(0);
  }
});

test('ADVANCED califica exactamente con 18-22 %', () => {
  expect(DevelopmentRatioEvaluator.computePenalty(0.17, toleranceOf('ADVANCED'))).toBeCloseTo(1);
  expect(DevelopmentRatioEvaluator.computePenalty(0.25, toleranceOf('ADVANCED'))).toBeCloseTo(3);
});

test('los niveles más altos califican más estricto el mismo DTR', () => {
  const penalties = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'].map((level) =>
    DevelopmentRatioEvaluator.computePenalty(0.3, toleranceOf(level))
  );
  expect(penalties[0]).toBeCloseTo(5);
  expect(penalties[1]).toBeCloseTo(6);
  expect(penalties[2]).toBeCloseTo(8);
});
