import { fireEvent, render, screen, within } from '@testing-library/react';
import RoastDataHistoryPanel from '../simulation/RoastDataHistoryPanel';
import esTexts from '../../locals/es.json';
import { getSessionDetail, getSessionHistory, getSessionSummary } from '../../services/progressService';
import { getRoastingFeedback } from '../../services/simulationService';
import { formatLocalDateTime } from '../../utils/dateFormat';

jest.mock('../../services/progressService', () => ({
  getSessionHistory: jest.fn(),
  getSessionSummary: jest.fn(),
  getSessionDetail: jest.fn(),
}));

jest.mock('../../services/simulationService', () => ({
  getRoastingFeedback: jest.fn(),
}));

const texts = esTexts.simulation.dataHistory;

const perfectSession = {
  id: 7,
  createdAt: '2026-09-25T02:30:00Z',
  knowledgeLevel: 'INTERMEDIATE',
  result: 'PERFECT',
  qualityScore: 73,
  finalTemperature: 200.19,
  totalDurationSeconds: 780,
  developmentTimeRatio: 0.192,
};

// Sesión guardada antes de RF009 y sin first crack: sin nivel ni DTR.
const legacyRawSession = {
  id: 3,
  createdAt: '2026-09-20T15:00:00Z',
  knowledgeLevel: null,
  result: 'RAW',
  qualityScore: 33,
  finalTemperature: 162.25,
  totalDurationSeconds: 300,
  developmentTimeRatio: null,
};

const page = (content, extra = {}) => ({ content, number: 0, totalPages: 1, totalElements: content.length, ...extra });

const renderPanel = () =>
  render(<RoastDataHistoryPanel texts={texts} isOpen onClose={jest.fn()} />);

// Fila 0 = encabezado; las sesiones siguen en el orden que envía el servidor.
const dataRow = (index) => screen.getAllByRole('row')[index];

describe('RoastDataHistoryPanel', () => {
  beforeEach(() => {
    getSessionSummary.mockResolvedValue({ bestScore: 91, averageScore: 53.5, totalSessions: 2 });
    getSessionHistory.mockResolvedValue(page([perfectSession, legacyRawSession]));
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  test('replaces the "not available" notice with the real history and summary', async () => {
    renderPanel();

    expect(await screen.findByText(formatLocalDateTime(perfectSession.createdAt))).toBeInTheDocument();
    expect(screen.queryByText(/todavía no está disponible/)).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: texts.title })).toBeInTheDocument();
    expect(screen.getByText(texts.summary.best)).toBeInTheDocument();
    expect(screen.getByText('91%')).toBeInTheDocument();
    expect(screen.getByText('53.5%')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(getSessionHistory).toHaveBeenCalledWith({ page: 0, result: '' });
  });

  test('shows every column of a session, formatted', async () => {
    renderPanel();
    await screen.findByText(formatLocalDateTime(perfectSession.createdAt));

    const row = within(dataRow(1));
    expect(row.getByText(formatLocalDateTime(perfectSession.createdAt))).toBeInTheDocument();
    expect(row.getByText('Intermedio')).toBeInTheDocument();
    expect(row.getByText('Perfecto')).toHaveClass('result-pill', 'result-pill-perfect');
    expect(row.getByText('73%')).toBeInTheDocument();
    expect(row.getByText('200.2 °C')).toBeInTheDocument();
    expect(row.getByText('13.00 min')).toBeInTheDocument();
    expect(row.getByText('19.2 %')).toBeInTheDocument();
  });

  test('shows a dash when a session has no level or no DTR', async () => {
    renderPanel();
    await screen.findByText(formatLocalDateTime(legacyRawSession.createdAt));

    const row = within(dataRow(2));
    expect(row.getAllByText('—')).toHaveLength(2);
    expect(row.getByText('Crudo')).toHaveClass('result-pill-raw');
  });

  test('filters by result starting again from the first page', async () => {
    getSessionHistory.mockResolvedValueOnce(page([perfectSession], { totalPages: 3 }));
    renderPanel();
    await screen.findByText(formatLocalDateTime(perfectSession.createdAt));
    fireEvent.click(screen.getByRole('button', { name: texts.buttons.next }));
    await screen.findByText(formatLocalDateTime(perfectSession.createdAt));

    getSessionHistory.mockResolvedValueOnce(page([]));
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'BURNED' } });

    expect(await screen.findByText(texts.emptyFiltered)).toBeInTheDocument();
    expect(getSessionHistory).toHaveBeenLastCalledWith({ page: 0, result: 'BURNED' });
  });

  test('moves between pages', async () => {
    getSessionHistory.mockResolvedValue(page([perfectSession], { number: 0, totalPages: 2, totalElements: 11 }));
    renderPanel();

    expect(await screen.findByText('Página 1 de 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: texts.buttons.previous })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: texts.buttons.next }));

    await screen.findByText(formatLocalDateTime(perfectSession.createdAt));
    expect(getSessionHistory).toHaveBeenLastCalledWith({ page: 1, result: '' });
  });

  test('explains when the player has no sessions yet', async () => {
    getSessionSummary.mockResolvedValue({ bestScore: null, averageScore: null, totalSessions: 0 });
    getSessionHistory.mockResolvedValue(page([], { totalPages: 0 }));
    renderPanel();

    expect(await screen.findByText(texts.empty)).toBeInTheDocument();
    expect(screen.getAllByText('—')).toHaveLength(2);
  });

  test('shows an error without breaking the panel when the history cannot load', async () => {
    getSessionHistory.mockRejectedValue(new Error('Sin conexión'));
    renderPanel();

    expect(await screen.findByText(texts.loadError)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: texts.close })).toBeInTheDocument();
  });

  test('opens the detail of a session and requests the AI feedback again', async () => {
    getSessionDetail.mockResolvedValue({
      ...perfectSession,
      chargeTemperature: 190,
      targetTemperature: 200,
      peakTemperature: 200.56,
      firstCrackReached: true,
      developmentTimeSeconds: 150,
    });
    getRoastingFeedback.mockResolvedValue('Buen control del desarrollo.');
    renderPanel();
    await screen.findByText(formatLocalDateTime(perfectSession.createdAt));

    fireEvent.click(within(dataRow(1)).getByRole('button', { name: texts.buttons.view }));

    expect(await screen.findByRole('heading', { name: texts.detail.title })).toBeInTheDocument();
    expect(getSessionDetail).toHaveBeenCalledWith(7);
    expect(screen.getByText(texts.detail.chargeTemp)).toBeInTheDocument();
    expect(screen.getByText('190.0 °C')).toBeInTheDocument();
    expect(screen.getByText('200.6 °C')).toBeInTheDocument();
    expect(screen.getByText(texts.detail.yes)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: texts.buttons.feedback }));
    expect(await screen.findByText('Buen control del desarrollo.')).toBeInTheDocument();
    expect(getRoastingFeedback).toHaveBeenCalledWith(7);

    fireEvent.click(screen.getByRole('button', { name: texts.buttons.back }));
    expect(await screen.findByText(texts.summary.best)).toBeInTheDocument();
  });

  test('tells the player when the AI feedback is not available', async () => {
    getSessionDetail.mockResolvedValue({ ...perfectSession, firstCrackReached: true });
    getRoastingFeedback.mockRejectedValue(new Error('Ollama no responde'));
    renderPanel();
    await screen.findByText(formatLocalDateTime(perfectSession.createdAt));

    fireEvent.click(within(dataRow(1)).getByRole('button', { name: texts.buttons.view }));
    fireEvent.click(await screen.findByRole('button', { name: texts.buttons.feedback }));

    expect(await screen.findByText(texts.feedback.unavailable)).toBeInTheDocument();
  });

  test('renders nothing while closed', () => {
    render(<RoastDataHistoryPanel texts={texts} isOpen={false} onClose={jest.fn()} />);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(getSessionHistory).not.toHaveBeenCalled();
  });
});
