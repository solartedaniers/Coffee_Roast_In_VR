import { REFERENCE_BATCH_WEIGHT_KG } from './RoastConstants';

// ================================================================
// GreenBeanProfile
// Objeto de valor: describe el lote de grano verde de una sesión de
// tueste (densidad, humedad inicial, tamaño de malla, peso del lote).
// Inmutable — se sortea una vez al iniciar la simulación y no cambia
// durante el tueste.
// ================================================================
export default class GreenBeanProfile {
  constructor({ density, moisture0Pct, meshSize, batchWeightKg, entryTempC }) {
    this.density = density;
    this.moisture0Pct = moisture0Pct;
    this.meshSize = meshSize;
    this.batchWeightKg = batchWeightKg;
    this.entryTempC = entryTempC;
    // Lotes más grandes que el de referencia calientan más lento (más masa
    // térmica que mover con el mismo quemador) y viceversa.
    this.thermalMassFactor = REFERENCE_BATCH_WEIGHT_KG / batchWeightKg;
  }
}
