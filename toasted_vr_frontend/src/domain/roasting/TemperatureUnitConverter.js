import { TEMPERATURE_UNITS } from './RoastConstants';

// ================================================================
// TemperatureUnitConverter
// Responsabilidad única: convertir una lectura en Celsius (la única
// fuente de verdad del modelo) a la unidad que el operador eligió
// mostrar, y de vuelta cuando escribe un valor en esa unidad.
// ================================================================
export default class TemperatureUnitConverter {
  static toDisplay(celsius, unit) {
    return unit === TEMPERATURE_UNITS.FAHRENHEIT ? (celsius * 9) / 5 + 32 : celsius;
  }

  static toCelsius(displayValue, unit) {
    return unit === TEMPERATURE_UNITS.FAHRENHEIT ? ((displayValue - 32) * 5) / 9 : displayValue;
  }

  static unitSymbol(unit) {
    return unit === TEMPERATURE_UNITS.FAHRENHEIT ? '°F' : '°C';
  }
}
