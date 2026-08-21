import GreenBeanProfile from './GreenBeanProfile';
import {
  BEAN_DENSITY_MIN,
  BEAN_DENSITY_MAX,
  BEAN_MOISTURE_MIN_PCT,
  BEAN_MOISTURE_MAX_PCT,
  BEAN_MESH_SIZE_MIN,
  BEAN_MESH_SIZE_MAX,
  BATCH_WEIGHT_MIN_KG,
  BATCH_WEIGHT_MAX_KG,
  BEAN_ENTRY_TEMP_MIN_C,
  BEAN_ENTRY_TEMP_MAX_C,
} from './RoastConstants';

function randomInRange(min, max) {
  return min + Math.random() * (max - min);
}

// ================================================================
// GreenBeanProfileFactory
// Responsabilidad única: sortear un GreenBeanProfile plausible para
// una nueva sesión de tueste, mismo patrón que
// FirstCrackDetector.pickThresholdTemperature() — aleatorio dentro de
// un rango realista, una vez por sesión.
// ================================================================
export default class GreenBeanProfileFactory {
  static createRandom() {
    return new GreenBeanProfile({
      density: parseFloat(randomInRange(BEAN_DENSITY_MIN, BEAN_DENSITY_MAX).toFixed(2)),
      moisture0Pct: parseFloat(randomInRange(BEAN_MOISTURE_MIN_PCT, BEAN_MOISTURE_MAX_PCT).toFixed(1)),
      meshSize: Math.round(randomInRange(BEAN_MESH_SIZE_MIN, BEAN_MESH_SIZE_MAX)),
      batchWeightKg: parseFloat(randomInRange(BATCH_WEIGHT_MIN_KG, BATCH_WEIGHT_MAX_KG).toFixed(1)),
      entryTempC: parseFloat(randomInRange(BEAN_ENTRY_TEMP_MIN_C, BEAN_ENTRY_TEMP_MAX_C).toFixed(1)),
    });
  }
}
