// ================================================================
// RoastCurveProfile
// Objeto de valor: una curva programada (Inicio / Crepitación / Final)
// de las 3 que ofrece "Programación de Curvas" en el menú. Por ahora
// solo es forma de datos — no se aplica todavía a ningún tueste real;
// eso es decisión de una fase posterior (ver Parte 3 del plan).
// ================================================================
export default class RoastCurveProfile {
  constructor({ curveNumber, startTemp, crackleTemp, finalTemp }) {
    this.curveNumber = curveNumber;
    this.startTemp = startTemp;
    this.crackleTemp = crackleTemp;
    this.finalTemp = finalTemp;
  }
}
