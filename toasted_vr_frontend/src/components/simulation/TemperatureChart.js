import React from 'react';
import ChartAxisScale from '../../domain/roasting/ChartAxisScale';
import TemperatureUnitConverter from '../../domain/roasting/TemperatureUnitConverter';
import {
  CHART_VERTICAL_STEP_C,
  CHART_VERTICAL_TICK_COUNT,
  CHART_FLOOR_REFERENCE_TEMP_C,
  CHART_TIME_WINDOW_SECONDS,
} from '../../domain/roasting/RoastConstants';

const VIEW_W = 820;
const VIEW_H = 230;
// bottom reducido: ya no hay números ni título en el eje horizontal, solo
// la línea base — el tiempo total ya se ve en la casilla "Tiempo Tueste"
// del panel de control, no hace falta repetirlo aquí.
const PAD = { top: 15, right: 20, bottom: 12, left: 46 };

// Margen vacío antes del primer punto y después del último en la vista de
// resultado final (fitToData) — para que quede claro que la curva empieza/
// termina ahí porque el proceso real empezó/terminó ahí, no porque se acabó
// el espacio del gráfico. Solo aplica ahí: en vivo, el punto más reciente
// debe seguir tocando el borde (así se ve una ventana deslizante en tiempo
// real).
const EDGE_PADDING_PX = 40;

// ================================================================
// TemperatureChart
// Responsabilidad única: dibujar la curva de temperatura — un
// gráfico de líneas en SVG con eje vertical de 6 números que se
// desplaza solo, más dos líneas de referencia sin etiquetas de
// texto (porque esos valores ya se muestran en las casillas del
// panel de control): un piso fijo (CHART_FLOOR_REFERENCE_TEMP_C, no
// depende de ningún slider) y un techo que es la Temp Ctrl que fijó
// el operador. El eje horizontal es tiempo real a velocidad fija (ver
// pxPerSecond más abajo), sin números (el tiempo total ya se ve en la
// casilla "Tiempo Tueste" del panel de control).
//
// Toda la geometría interna (dónde caen las líneas) se calcula
// siempre en Celsius para que el eje y la curva nunca se desalineen;
// solo el texto de los números se convierte a la unidad elegida.
// ================================================================
export default function TemperatureChart({
  data,
  targetTemperature,
  sensorCalibrationOffsetC = 0,
  temperatureUnit,
  chartStepC = CHART_VERTICAL_STEP_C,
  texts,
  // Vista de resultado final de un tueste largo: en vez de recortar el
  // proceso completo al tamaño normal, el viewBox crece en la misma
  // escala real (píxeles por segundo/grado de una sesión típica) para
  // que quepa completo — y es el propio SVG (preserveAspectRatio, ver
  // el estilo aspectRatio más abajo) el que encoge el dibujo entero
  // para que quepa en el mismo espacio en pantalla, sin recortar ni
  // deformar proporciones. La vista en vivo (fitToData=false, default)
  // no cambia: sigue con el tamaño fijo de siempre.
  fitToData = false,
}) {
  if (!data || data.length < 2) return null;

  const basePlotW = VIEW_W - PAD.left - PAD.right;
  const basePlotH = VIEW_H - PAD.top - PAD.bottom;

  const calibratedData = data.map((point) => ({ time: point.time, temp: point.temp + sensorCalibrationOffsetC }));

  const maxObservedTemp = Math.max(
    targetTemperature,
    ...calibratedData.map((point) => point.temp)
  );
  const minObservedTemp = Math.min(
    CHART_FLOOR_REFERENCE_TEMP_C,
    ...calibratedData.map((point) => point.temp)
  );

  const verticalTicks = ChartAxisScale.computeVerticalTicks(minObservedTemp, maxObservedTemp, chartStepC);
  const minTick = verticalTicks[0];
  const maxTick = verticalTicks[verticalTicks.length - 1];

  const minTime = calibratedData[0].time;
  // Velocidad fija de píxeles/segundo, calculada sobre la ventana móvil
  // real (CHART_TIME_WINDOW_SECONDS) — no una duración asumida aparte —
  // así la ventana en vivo llena todo el ancho apenas se completa, en vez
  // de ocupar solo una fracción. NO se recalcula según cuántos puntos
  // existan en cada momento: con esto, un punto ya dibujado nunca se
  // vuelve a mover, la curva solo avanza hacia la derecha con cada
  // segundo real, igual que en la máquina real.
  const pxPerSecond = basePlotW / CHART_TIME_WINDOW_SECONDS;
  const pxPerDegreeReference = basePlotH / ((CHART_VERTICAL_TICK_COUNT - 1) * chartStepC);

  const actualDurationSeconds = calibratedData[calibratedData.length - 1].time - minTime;
  const dataPlotW = fitToData ? Math.max(basePlotW, actualDurationSeconds * pxPerSecond) : basePlotW;
  const edgePaddingPx = fitToData ? EDGE_PADDING_PX : 0;
  const plotW = dataPlotW + 2 * edgePaddingPx;
  const plotH = fitToData ? Math.max(basePlotH, (maxTick - minTick) * pxPerDegreeReference) : basePlotH;

  const xScale = (timeSeconds) => edgePaddingPx + Math.min(dataPlotW, (timeSeconds - minTime) * pxPerSecond);
  const yScale = (temp) => plotH - ((temp - minTick) / (maxTick - minTick)) * plotH;
  const formatTemp = (celsius) => Math.round(TemperatureUnitConverter.toDisplay(celsius, temperatureUnit));

  const pathD = calibratedData.reduce((acc, pt, i) => {
    const x = xScale(pt.time).toFixed(1);
    const y = yScale(pt.temp).toFixed(1);
    return acc + `${i === 0 ? 'M' : 'L'}${x},${y} `;
  }, '');

  const viewBoxW = plotW + PAD.left + PAD.right;
  const viewBoxH = plotH + PAD.top + PAD.bottom;

  return (
    <div className="chart-wrapper">
      <svg
        viewBox={`0 0 ${viewBoxW} ${viewBoxH}`}
        width="100%"
        // Proporción fija en pantalla (la de siempre) sin importar qué tan
        // grande sea el viewBox interno — así preserveAspectRatio="xMidYMid
        // meet" (el default de SVG) encoge el contenido completo para que
        // quepa en el mismo espacio, en vez de que el elemento crezca de
        // alto al tener un viewBox más grande.
        style={{ aspectRatio: `${VIEW_W} / ${VIEW_H}` }}
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

          {/* Línea de referencia: piso fijo, no depende de ningún slider */}
          <line
            x1={0} y1={yScale(CHART_FLOOR_REFERENCE_TEMP_C)} x2={plotW} y2={yScale(CHART_FLOOR_REFERENCE_TEMP_C)}
            stroke="var(--color-chart-charge-line)" strokeDasharray="6 3" strokeWidth={1.5}
          />

          {/* Línea de referencia: Temp Ctrl (el panel de arriba ya la identifica) */}
          <line
            x1={0} y1={yScale(targetTemperature)} x2={plotW} y2={yScale(targetTemperature)}
            stroke="var(--color-chart-target-line)" strokeDasharray="6 3" strokeWidth={1.5}
          />

          {/* Curva de temperatura del grano */}
          <path d={pathD} fill="none" stroke="var(--color-chart-temp-line)" strokeWidth={2.5} strokeLinejoin="round" />

          <line x1={0} y1={plotH} x2={plotW} y2={plotH} stroke="var(--color-border)" />

          <text
            transform={`translate(-32,${plotH / 2}) rotate(-90)`}
            fontSize={11} fill="var(--color-ink-muted)" textAnchor="middle"
          >
            {texts.tempLabel} ({TemperatureUnitConverter.unitSymbol(temperatureUnit)})
          </text>
        </g>
      </svg>
    </div>
  );
}
