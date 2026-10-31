import { render, screen } from '@testing-library/react';
import RoastResultsPanel from './RoastResultsPanel';
import RoastQualityEvaluator from '../../domain/roasting/RoastQualityEvaluator';
import esTexts from '../../locals/es.json';

const baseProps = {
  savingState: 'idle',
  saveErrorDetail: '',
  feedbackState: 'idle',
  feedbackText: '',
  temperatureUnit: 'C',
  texts: esTexts.simulation.results,
  onNewSimulation: jest.fn(),
};

// Regresión del hallazgo real: first crack SÍ se alcanzó y luego el café se
// enfrió hasta 166°C antes de descargar — badge y texto ya no deben decir
// que nunca se acercó al first crack.
test('escenario reportado (first crack alcanzado, se enfría a 166°C): badge y texto usan RAW_REGRESSED', () => {
  const finishedSim = {
    finalTemperature: 166,
    maxConsecutiveBurnSeconds: 0,
    burnedFlag: false,
    firstCrackReached: true,
    firstCrackTimeSeconds: 480,
    roastingElapsedSeconds: 700,
    chargeTemperature: 195,
    maillardStagnationSeconds: 0,
    maillardStagnationFlag: false,
  };
  const roastResult = RoastQualityEvaluator.evaluate(finishedSim, 'INTERMEDIATE');
  expect(roastResult.result).toBe('RAW');

  render(<RoastResultsPanel {...baseProps} sim={finishedSim} roastResult={roastResult} />);

  expect(screen.getByText('Desarrollo interrumpido')).toBeInTheDocument();
  expect(screen.queryByText('Café Crudo')).not.toBeInTheDocument();
  expect(
    screen.getByText(/Se alcanzó el first crack, pero la temperatura bajó demasiado/)
  ).toBeInTheDocument();
  expect(screen.getByText('Sí')).toBeInTheDocument(); // First crack alcanzado: Sí
});

// Sin regresión: un RAW normal (nunca llegó a first crack) sigue mostrando
// el badge y el texto de siempre.
test('RAW normal (nunca alcanza first crack) conserva el badge y texto previos', () => {
  const finishedSim = {
    finalTemperature: 150,
    maxConsecutiveBurnSeconds: 0,
    burnedFlag: false,
    firstCrackReached: false,
    firstCrackTimeSeconds: null,
    roastingElapsedSeconds: 300,
    chargeTemperature: 195,
    maillardStagnationSeconds: 0,
    maillardStagnationFlag: false,
  };
  const roastResult = RoastQualityEvaluator.evaluate(finishedSim, 'INTERMEDIATE');
  expect(roastResult.result).toBe('RAW');

  render(<RoastResultsPanel {...baseProps} sim={finishedSim} roastResult={roastResult} />);

  expect(screen.getByText('Café Crudo')).toBeInTheDocument();
  expect(screen.queryByText('Desarrollo interrumpido')).not.toBeInTheDocument();
});

// RF015: si el servidor rechaza la sesión, el jugador ve el motivo que envía.
test('muestra el motivo cuando el servidor rechaza la sesión', () => {
  const finishedSim = {
    finalTemperature: 150,
    maxConsecutiveBurnSeconds: 0,
    burnedFlag: false,
    firstCrackReached: false,
    firstCrackTimeSeconds: null,
    roastingElapsedSeconds: 300,
    chargeTemperature: 195,
    maillardStagnationSeconds: 0,
    maillardStagnationFlag: false,
  };
  const roastResult = RoastQualityEvaluator.evaluate(finishedSim, 'INTERMEDIATE');
  const serverMessage = 'No se guardó la sesión: la temperatura de carga (900 °C) debe estar entre 0 y 750 °C.';

  render(
    <RoastResultsPanel
      {...baseProps}
      savingState="error"
      saveErrorDetail={serverMessage}
      sim={finishedSim}
      roastResult={roastResult}
    />
  );

  expect(screen.getByText(`${esTexts.simulation.results.saveError} (${serverMessage})`)).toBeInTheDocument();
  expect(screen.queryByText(esTexts.simulation.results.saved)).not.toBeInTheDocument();
});

// Fase 5: al guardar, el panel muestra la calificación combinada que devolvió el servidor.
test('muestra la calificación de la sesión guardada y avisa mientras la IA califica', () => {
  const finishedSim = {
    finalTemperature: 150,
    maxConsecutiveBurnSeconds: 0,
    burnedFlag: false,
    firstCrackReached: false,
    firstCrackTimeSeconds: null,
    roastingElapsedSeconds: 300,
    chargeTemperature: 195,
    maillardStagnationSeconds: 0,
    maillardStagnationFlag: false,
  };
  const roastResult = RoastQualityEvaluator.evaluate(finishedSim, 'INTERMEDIATE');
  const savedSession = {
    id: 9,
    qualityScore: roastResult.score,
    finalScore: roastResult.score,
    grade: 'POOR',
    evaluation: { status: 'PENDING', criteria: [], notApplicable: [] },
  };
  const evaluationTexts = esTexts.simulation.dataHistory;

  render(
    <RoastResultsPanel
      {...baseProps}
      savingState="saved"
      sim={finishedSim}
      roastResult={roastResult}
      savedSession={savedSession}
      evaluationTexts={evaluationTexts}
    />
  );

  expect(screen.getByText(evaluationTexts.evaluation.title)).toBeInTheDocument();
  expect(screen.getByText(`${roastResult.score}% · Deficiente`)).toBeInTheDocument();
  expect(screen.getByText(evaluationTexts.evaluation.pending)).toBeInTheDocument();
});

test('la barra muestra la nota final de la sesión guardada en vez de la de las reglas', () => {
  const finishedSim = {
    finalTemperature: 150,
    maxConsecutiveBurnSeconds: 0,
    burnedFlag: false,
    firstCrackReached: false,
    firstCrackTimeSeconds: null,
    roastingElapsedSeconds: 300,
    chargeTemperature: 195,
    maillardStagnationSeconds: 0,
    maillardStagnationFlag: false,
  };
  const roastResult = RoastQualityEvaluator.evaluate(finishedSim, 'INTERMEDIATE');
  const finalScore = roastResult.score + 7;
  const savedSession = {
    id: 9,
    qualityScore: roastResult.score,
    finalScore,
    grade: 'POOR',
    evaluation: { status: 'COMPLETED', criteria: [], notApplicable: [] },
  };

  render(
    <RoastResultsPanel
      {...baseProps}
      savingState="saved"
      sim={finishedSim}
      roastResult={roastResult}
      savedSession={savedSession}
      evaluationTexts={esTexts.simulation.dataHistory}
    />
  );

  expect(screen.getByText(`${finalScore}%`)).toBeInTheDocument();
  expect(screen.getByTestId('score-bar-fill')).toHaveStyle({ width: `${finalScore}%` });
});

test('sin sesión guardada la barra muestra la nota de las reglas', () => {
  const finishedSim = {
    finalTemperature: 150,
    maxConsecutiveBurnSeconds: 0,
    burnedFlag: false,
    firstCrackReached: false,
    firstCrackTimeSeconds: null,
    roastingElapsedSeconds: 300,
    chargeTemperature: 195,
    maillardStagnationSeconds: 0,
    maillardStagnationFlag: false,
  };
  const roastResult = RoastQualityEvaluator.evaluate(finishedSim, 'INTERMEDIATE');

  render(<RoastResultsPanel {...baseProps} sim={finishedSim} roastResult={roastResult} />);

  expect(screen.getByText(`${roastResult.score}%`)).toBeInTheDocument();
});
