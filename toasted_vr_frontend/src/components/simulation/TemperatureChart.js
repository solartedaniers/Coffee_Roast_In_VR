import React from 'react';
import ChartAxisScale from '../../domain/roasting/ChartAxisScale';
import RoastMetrics from '../../domain/roasting/RoastMetrics';
import TemperatureUnitConverter from '../../domain/roasting/TemperatureUnitConverter';
import { CHART_VERTICAL_STEP_C, CHART_HORIZONTAL_TICK_COUNT } from '../../domain/roasting/RoastConstants';

const VIEW_W = 820;
const VIEW_H = 230;
const PAD = { top: 15, right: 20, bottom: 40, left: 46 };

// ================================================================
// TemperatureChart
// Responsabilidad única: dibujar la curva de temperatura en vivo —
// un gráfico de líneas en SVG con eje vertical de 6 números que se
// desplaza solo, más las dos líneas de referencia (carga / Temp
// Ctrl) sin etiquetas de texto, porque esos valores ya se muestran
// en las casillas del panel de control.
//
// Toda la geometría interna (dónde caen las líneas) se calcula
// siempre en Celsius para que el eje y la curva nunca se desalineen;
// solo el texto de los números se convierte a la unidad elegida.
// ================================================================
export default function TemperatureChart({
  data,
  chargeTemperature,
  targetTemperature,
  sensorCalibrationOffsetC = 0,
  temperatureUnit,
  chartStepC = CHART_VERTICAL_STEP_C,
  texts,
}) {
  if (!data || data.length < 2) return null;

  const plotW = VIEW_W - PAD.left - PAD.right;
  const plotH = VIEW_H - PAD.top - PAD.bottom;

  const calibratedData = data.map((point) => ({ time: point.time, temp: point.temp + sensorCalibrationOffsetC }));

  const minTimeMinutes = calibratedData[0].time / 60;
  const maxTimeMinutes = calibratedData[calibratedData.length - 1].time / 60;
  const maxObservedTemp = Math.max(
    targetTemperature,
    ...calibratedData.map((point) => point.temp)
  );

  const verticalTicks = ChartAxisScale.computeVerticalTicks(maxObservedTemp, chartStepC);
  const timeTicks = ChartAxisScale.computeHorizontalTicks(minTimeMinutes, maxTimeMinutes, CHART_HORIZONTAL_TICK_COUNT);

  const minTick = verticalTicks[0];
  const maxTick = verticalTicks[verticalTicks.length - 1];
  const minTimeTick = timeTicks[0];
  const maxTimeTick = timeTicks[timeTicks.length - 1];

  const xScale = (timeSeconds) => ((timeSeconds / 60 - minTimeTick) / (maxTimeTick - minTimeTick)) * plotW;
  const yScale = (temp) => plotH - ((temp - minTick) / (maxTick - minTick)) * plotH;
  const formatTemp = (celsius) => Math.round(TemperatureUnitConverter.toDisplay(celsius, temperatureUnit));

  const pathD = calibratedData.reduce((acc, pt, i) => {
    const x = xScale(pt.time).toFixed(1);
    const y = yScale(pt.temp).toFixed(1);
    return acc + `${i === 0 ? 'M' : 'L'}${x},${y} `;
  }, '');

  return (
    <div className="chart-wrapper">
      <svg
        viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
        width="100%"
        className="temperature-chart"
        aria-label={texts.title}
      >
        <g transform={`translate(${PAD.left},${PAD.top})`}>
          {verticalTicks.map((temp) => (
            <g key={temp}>
              <line
                x1={0} y1={yScale(temp)} x2={plotW} y2={yScale(temp)}
                stroke="var(--color-border)" strokeDasharray="4 3" strokeWidth={1}
              />
              <text
                x={-8} y={yScale(temp) + 4}
                fontSize={11} fill="var(--color-ink-muted)" textAnchor="end"
              >
                {formatTemp(temp)}
              </text>
            </g>
          ))}

          {/* Línea de referencia: temperatura de carga (el panel de arriba ya la identifica) */}
          <line
            x1={0} y1={yScale(chargeTemperature)} x2={plotW} y2={yScale(chargeTemperature)}
            stroke="var(--color-chart-charge-line)" strokeDasharray="6 3" strokeWidth={1.5}
          />

          {/* Línea de referencia: Temp Ctrl (el panel de arriba ya la identifica) */}
          <line
            x1={0} y1={yScale(targetTemperature)} x2={plotW} y2={yScale(targetTemperature)}
            stroke="var(--color-chart-target-line)" strokeDasharray="6 3" strokeWidth={1.5}
          />

          {/* Curva de temperatura del café en vivo */}
          <path d={pathD} fill="none" stroke="var(--color-chart-temp-line)" strokeWidth={2.5} strokeLinejoin="round" />

          <line x1={0} y1={plotH} x2={plotW} y2={plotH} stroke="var(--color-border)" />

          {timeTicks.map((t) => (
            <g key={t}>
              <line x1={xScale(t * 60)} y1={plotH} x2={xScale(t * 60)} y2={plotH + 5} stroke="var(--color-border)" />
              <text
                x={xScale(t * 60).toFixed(1)} y={plotH + 18}
                fontSize={10} fill="var(--color-ink-muted)" textAnchor="middle"
              >
                {RoastMetrics.formatDecimalMinutes(t * 60)}
              </text>
            </g>
          ))}

          <text
            transform={`translate(-32,${plotH / 2}) rotate(-90)`}
            fontSize={11} fill="var(--color-ink-muted)" textAnchor="middle"
          >
            {texts.tempLabel} ({TemperatureUnitConverter.unitSymbol(temperatureUnit)})
          </text>
          <text
            x={plotW / 2} y={plotH + 32}
            fontSize={11} fill="var(--color-ink-muted)" textAnchor="middle"
          >
            {texts.timeLabel}
          </text>
        </g>
      </svg>
    </div>
  );
}
