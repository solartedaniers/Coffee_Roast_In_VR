import { HEAT_TRANSFER_COEFF_PER_SEC, SPECIFIC_HEAT_MOISTURE_FACTOR, BEAN_MOISTURE_REFERENCE_PCT } from './RoastConstants';

// ================================================================
// ConductiveHeatGainTerm
// Estrategia de calor (contrato computeRatePerSecond(context)): calor
// que el grano gana por conducción desde el aire, atenuado por su
// densidad (grano más denso absorbe más lento) y por un "calor
// específico efectivo" que sube con la humedad restante (grano más
// húmedo necesita más energía para subir un grado).
// ================================================================
export default class ConductiveHeatGainTerm {
  static computeRatePerSecond({ airTemp, beanTemp, density, moisturePct, thermalMassFactor }) {
    const specificHeatFactor = 1 + SPECIFIC_HEAT_MOISTURE_FACTOR * (moisturePct - BEAN_MOISTURE_REFERENCE_PCT);
    const coeff = (HEAT_TRANSFER_COEFF_PER_SEC * thermalMassFactor) / density / specificHeatFactor;
    return coeff * (airTemp - beanTemp);
  }
}
