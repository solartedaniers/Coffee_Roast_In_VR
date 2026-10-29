import { act, render, renderHook, screen } from '@testing-library/react';
import RoastEvaluationBreakdown from './RoastEvaluationBreakdown';
import useSessionEvaluation from './useSessionEvaluation';
import esTexts from '../../locals/es.json';
import { getSessionDetail } from '../../services/progressService';
import { evaluationPollIntervalMs, evaluationPollMaxAttempts } from '../../config/env';

jest.mock('../../services/progressService', () => ({
  getSessionDetail: jest.fn(),
}));

const texts = esTexts.simulation.dataHistory;
const evaluationTexts = texts.evaluation;

const completedSession = {
  id: 7,
  qualityScore: 74,
  finalScore: 80,
  grade: 'GOOD',
  evaluation: {
    status: 'COMPLETED',
    skipReason: null,
    finalScore: 80,
    rulesScore: 74,
    rubricScore: 93.3,
    rulesWeight: 0.7,
    aiWeight: 0.3,
    defectCapApplied: false,
    criteria: [
      { id: 'DRYING_GAS', level: 3, maxLevel: 3, justification: 'Mantuviste la subida en franja.' },
      { id: 'RISE_TO_FIRST_CRACK', level: 2, maxLevel: 3, justification: 'Llegaste al crack a tiempo.' },
    ],
    notApplicable: ['ALERT_REACTION'],
  },
};

const pendingSession = {
  ...completedSession,
  finalScore: 74,
  evaluation: { ...completedSession.evaluation, status: 'PENDING', finalScore: null, criteria: [], notApplicable: [] },
};

const renderBreakdown = (session, timedOut = false) =>
  render(<RoastEvaluationBreakdown texts={texts} session={session} timedOut={timedOut} />);

describe('RoastEvaluationBreakdown', () => {
  test('muestra la nota final con su calificación, el peso de cada parte y cada criterio', () => {
    renderBreakdown(completedSession);

    expect(screen.getByText('80% · Bueno')).toBeInTheDocument();
    expect(screen.getByText('Reglas del tueste (70 %)')).toBeInTheDocument();
    expect(screen.getByText('74%')).toBeInTheDocument();
    expect(screen.getByText('Tus decisiones según la IA (30 %)')).toBeInTheDocument();
    expect(screen.getByText('93.3%')).toBeInTheDocument();
    expect(screen.getByText('Gas en el secado: nivel 3 de 3.')).toBeInTheDocument();
    expect(screen.getByText('Mantuviste la subida en franja.', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('No aplicó en este tueste: Reacción a las alertas.')).toBeInTheDocument();
    expect(screen.queryByText(evaluationTexts.defectCap)).not.toBeInTheDocument();
  });

  test('avisa cuando el tope por defecto recortó la nota', () => {
    renderBreakdown({
      ...completedSession,
      finalScore: 41,
      grade: 'POOR',
      evaluation: { ...completedSession.evaluation, defectCapApplied: true },
    });

    expect(screen.getByText('41% · Deficiente')).toBeInTheDocument();
    expect(screen.getByText(evaluationTexts.defectCap)).toBeInTheDocument();
  });

  test('mientras está pendiente vale la nota de las reglas, y avisa si tarda', () => {
    const { rerender } = renderBreakdown(pendingSession);
    expect(screen.getByText('74% · Bueno')).toBeInTheDocument();
    expect(screen.getByText(evaluationTexts.pending)).toBeInTheDocument();
    expect(screen.queryByText(evaluationTexts.criteriaTitle)).not.toBeInTheDocument();

    rerender(<RoastEvaluationBreakdown texts={texts} session={pendingSession} timedOut />);
    expect(screen.getByText(evaluationTexts.slow)).toBeInTheDocument();
  });

  test.each([
    ['AUTO_MODE'],
    ['NO_TRACE'],
    ['INVALID_TRACE'],
    ['NO_CRITERIA'],
  ])('explica por qué no calificó la IA (%s)', (skipReason) => {
    renderBreakdown({
      ...completedSession,
      finalScore: 74,
      evaluation: { ...pendingSession.evaluation, status: 'NOT_APPLICABLE', skipReason, finalScore: 74 },
    });

    expect(screen.getByText(evaluationTexts.skipReasons[skipReason])).toBeInTheDocument();
  });

  test('cuando la IA falló o la sesión es anterior a la calificación combinada', () => {
    const { unmount } = renderBreakdown({ ...pendingSession, evaluation: { ...pendingSession.evaluation, status: 'FAILED' } });
    expect(screen.getByText(evaluationTexts.failed)).toBeInTheDocument();
    unmount();

    renderBreakdown({ id: 3, qualityScore: 38, finalScore: 38, grade: 'POOR', evaluation: null });
    expect(screen.getByText('38% · Deficiente')).toBeInTheDocument();
    expect(screen.getByText(evaluationTexts.legacy)).toBeInTheDocument();
  });
});

describe('useSessionEvaluation', () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => {
    jest.useRealTimers();
    jest.clearAllMocks();
  });

  const tick = () => act(async () => {
    jest.advanceTimersByTime(evaluationPollIntervalMs);
  });

  test('vuelve a pedir la sesión pendiente hasta que llega la nota final', async () => {
    getSessionDetail.mockResolvedValueOnce(pendingSession).mockResolvedValueOnce(completedSession);
    const { result } = renderHook(() => useSessionEvaluation(pendingSession));

    await tick();
    expect(result.current.session.evaluation.status).toBe('PENDING');
    await tick();
    expect(result.current.session).toBe(completedSession);

    await tick();
    expect(getSessionDetail).toHaveBeenCalledTimes(2);
    expect(getSessionDetail).toHaveBeenCalledWith(7);
  });

  test('no pregunta por una sesión que ya está calificada', async () => {
    renderHook(() => useSessionEvaluation(completedSession));

    await tick();
    expect(getSessionDetail).not.toHaveBeenCalled();
  });

  test('deja de preguntar y avisa después del máximo de intentos, aunque falle la red', async () => {
    getSessionDetail.mockRejectedValue(new Error('Sin conexión'));
    const { result } = renderHook(() => useSessionEvaluation(pendingSession));

    for (let attempt = 0; attempt < evaluationPollMaxAttempts + 2; attempt += 1) {
      await tick();
    }
    expect(result.current.timedOut).toBe(true);
    expect(getSessionDetail).toHaveBeenCalledTimes(evaluationPollMaxAttempts);
  });
});
