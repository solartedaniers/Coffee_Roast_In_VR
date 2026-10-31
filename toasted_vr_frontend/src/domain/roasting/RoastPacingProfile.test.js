import RoastPacingProfile from './RoastPacingProfile';
import RoastThermalModel from './RoastThermalModel';
import {
  PHASES,
  AMBIENT_TEMP_C,
  FLAME_POWER_DEFAULT_PCT,
  CHARGE_TEMP_DEFAULT_C,
  BATCH_WEIGHT_MIN_KG,
  BATCH_WEIGHT_MAX_KG,
  REFERENCE_BATCH_WEIGHT_KG,
} from './RoastConstants';

function preheatSeconds(batchWeightKg) {
  const { airResponseRatePerSec } = RoastPacingProfile.forPhase(PHASES.PREHEAT);
  const thermalMassFactor = REFERENCE_BATCH_WEIGHT_KG / batchWeightKg;
  let airTemp = AMBIENT_TEMP_C;
  let seconds = 0;
  while (airTemp < CHARGE_TEMP_DEFAULT_C) {
    airTemp = RoastThermalModel.computeNextAirTemp(airTemp, FLAME_POWER_DEFAULT_PCT, thermalMassFactor, airResponseRatePerSec);
    seconds++;
  }
  return seconds;
}

test.each([BATCH_WEIGHT_MIN_KG, BATCH_WEIGHT_MAX_KG])(
  'con la llama de arranque, el precalentamiento de un lote de %i kg dura entre 8 y 10 min',
  (batchWeightKg) => {
    const seconds = preheatSeconds(batchWeightKg);
    expect(seconds).toBeGreaterThanOrEqual(8 * 60);
    expect(seconds).toBeLessThanOrEqual(10 * 60);
  }
);
