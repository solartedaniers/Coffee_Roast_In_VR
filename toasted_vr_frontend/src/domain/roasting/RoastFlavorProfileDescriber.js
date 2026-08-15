import {
  RESULTS,
  DTR_IDEAL_MIN_RATIO,
  DTR_IDEAL_MAX_RATIO,
  BURN_CONSECUTIVE_LIMIT_SEC,
  MAILLARD_STAGNATION_LIMIT_SEC,
  RAW_TEMP_CEILING_C,
} from './RoastConstants';

// ================================================================
// RoastFlavorProfileDescriber
// Responsabilidad única: elegir, a partir de los datos reales de un
// tueste terminado, qué fragmento de perfil de sabor describe mejor
// el resultado (nunca un texto fijo por resultado). Los umbrales
// siguen referencias típicas de café arábica: menor tiempo de
// desarrollo tras el first crack (DTR bajo) da notas más claras y
// afrutadas; DTR dentro del rango ideal da un perfil equilibrado de
// caramelo; DTR alto da notas más oscuras de chocolate/frutos secos.
// Devuelve solo la clave del texto — el texto en sí vive en es.json.
// ================================================================
export default class RoastFlavorProfileDescriber {
  static describe(finishedSim) {
    const {
      result,
      finalTemperature,
      roastingElapsedSeconds,
      firstCrackTimeSeconds,
      maxConsecutiveBurnSeconds,
      maillardStagnationSeconds,
    } = finishedSim;

    switch (result) {
      case RESULTS.RAW:
        return RAW_TEMP_CEILING_C - finalTemperature > 15 ? 'RAW_SEVERE' : 'RAW_CLOSE';

      case RESULTS.BURNED:
        return maxConsecutiveBurnSeconds - BURN_CONSECUTIVE_LIMIT_SEC > BURN_CONSECUTIVE_LIMIT_SEC
          ? 'BURNED_SEVERE'
          : 'BURNED_MILD';

      case RESULTS.BAKED:
        return maillardStagnationSeconds - MAILLARD_STAGNATION_LIMIT_SEC > MAILLARD_STAGNATION_LIMIT_SEC
          ? 'BAKED_SEVERE'
          : 'BAKED_MILD';

      default: {
        const developmentSeconds = roastingElapsedSeconds - (firstCrackTimeSeconds || 0);
        const dtr = firstCrackTimeSeconds ? developmentSeconds / roastingElapsedSeconds : 0;
        if (dtr < DTR_IDEAL_MIN_RATIO) return 'PERFECT_BRIGHT';
        if (dtr > DTR_IDEAL_MAX_RATIO) return 'PERFECT_DEEP';
        return 'PERFECT_BALANCED';
      }
    }
  }
}
