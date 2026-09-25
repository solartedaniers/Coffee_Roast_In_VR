import { fireEvent, render, screen, within } from '@testing-library/react';
import RoastDataHistoryPanel from '../simulation/RoastDataHistoryPanel';
import esTexts from '../../locals/es.json';
import { getRanking, getSessionHistory, getSessionSummary } from '../../services/progressService';
import { formatLocalDate } from '../../utils/dateFormat';

jest.mock('../../services/progressService', () => ({
  getRanking: jest.fn(),
  getSessionHistory: jest.fn(),
  getSessionSummary: jest.fn(),
  getSessionDetail: jest.fn(),
}));

jest.mock('../../services/simulationService', () => ({
  getRoastingFeedback: jest.fn(),
}));

const texts = esTexts.simulation.dataHistory;
const rankingTexts = texts.ranking;

const entry = (position, username, bestScore, achievedAt = '2026-09-01T12:00:00Z') => ({
  position,
  username,
  bestScore,
  achievedAt,
});

const intermediateRanking = {
  level: 'INTERMEDIATE',
  top: [entry(1, 'betoUser', 88, '2026-09-01T12:03:00Z'), entry(2, 'anaUser', 88), entry(3, 'viewerUser', 70)],
  me: entry(3, 'viewerUser', 70),
  hasSessionsInLevel: true,
};

const openRankingTab = () => {
  render(<RoastDataHistoryPanel texts={texts} isOpen onClose={jest.fn()} />);
  fireEvent.click(screen.getByRole('tab', { name: texts.tabs.ranking }));
};

// Fila 0 = encabezado.
const tableRows = () => screen.getAllByRole('row');

describe('Ranking tab', () => {
  beforeEach(() => {
    getSessionSummary.mockResolvedValue({ bestScore: null, averageScore: null, totalSessions: 0 });
    getSessionHistory.mockResolvedValue({ content: [], number: 0, totalPages: 0, totalElements: 0 });
    getRanking.mockResolvedValue(intermediateRanking);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  test('shows the Historial and Ranking tabs, starting on the history', async () => {
    render(<RoastDataHistoryPanel texts={texts} isOpen onClose={jest.fn()} />);

    expect(screen.getByRole('tab', { name: texts.tabs.history })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: texts.tabs.ranking })).toHaveAttribute('aria-selected', 'false');
    expect(await screen.findByText(texts.empty)).toBeInTheDocument();
    expect(getRanking).not.toHaveBeenCalled();
  });

  test('asks for the player level by default and shows position, username, best score and date', async () => {
    openRankingTab();

    expect(await screen.findByText('betoUser')).toBeInTheDocument();
    expect(getRanking).toHaveBeenCalledWith(null);
    expect(screen.getByRole('tab', { name: texts.tabs.ranking })).toHaveClass('roast-menu-mode-btn-active');
    expect(screen.getByRole('combobox')).toHaveValue('INTERMEDIATE');

    const firstRow = within(tableRows()[1]);
    expect(firstRow.getByText('1')).toBeInTheDocument();
    expect(firstRow.getByText('88%')).toBeInTheDocument();
    expect(firstRow.getByText(formatLocalDate('2026-09-01T12:03:00Z'))).toBeInTheDocument();
    expect(within(tableRows()[0]).getAllByRole('columnheader')).toHaveLength(4);
  });

  test('highlights the player row and shows their position', async () => {
    openRankingTab();
    await screen.findByText('viewerUser');

    expect(tableRows()[3]).toHaveClass('is-selected');
    expect(within(tableRows()[3]).getByText(/\(tú\)/)).toBeInTheDocument();
    expect(tableRows()[1]).not.toHaveClass('is-selected');
    expect(screen.getByText(rankingTexts.yourPosition)).toBeInTheDocument();
    expect(screen.getByText('#3')).toBeInTheDocument();
  });

  test('adds the player row after the top when they are outside of it', async () => {
    const top = Array.from({ length: 10 }, (_, index) => entry(index + 1, `rival${index}`, 99 - index));
    getRanking.mockResolvedValue({
      level: 'INTERMEDIATE',
      top,
      me: entry(12, 'viewerUser', 50),
      hasSessionsInLevel: true,
    });
    openRankingTab();
    await screen.findByText('rival0');

    // El separador "…" es decorativo (aria-hidden): no cuenta como fila.
    const rows = tableRows();
    expect(rows).toHaveLength(12);
    expect(screen.getByText('…')).toBeInTheDocument();
    expect(rows[11]).toHaveClass('is-selected');
    expect(within(rows[11]).getByText('12')).toBeInTheDocument();
    expect(screen.getByText('#12')).toBeInTheDocument();
  });

  test('changes the level with the selector', async () => {
    openRankingTab();
    await screen.findByText('betoUser');
    getRanking.mockResolvedValue({
      level: 'ADVANCED',
      top: [entry(1, 'expertUser', 95)],
      me: null,
      hasSessionsInLevel: false,
    });

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'ADVANCED' } });

    expect(await screen.findByText('expertUser')).toBeInTheDocument();
    expect(getRanking).toHaveBeenLastCalledWith('ADVANCED');
    expect(screen.getByText(rankingTexts.noSessionsInLevel)).toBeInTheDocument();
    expect(screen.queryByText(rankingTexts.yourPosition)).not.toBeInTheDocument();
  });

  test('says when nobody has roasts in the level', async () => {
    getRanking.mockResolvedValue({ level: 'BEGINNER', top: [], me: null, hasSessionsInLevel: false });
    openRankingTab();

    expect(await screen.findByText(rankingTexts.empty)).toBeInTheDocument();
    expect(screen.getByText(rankingTexts.noSessionsInLevel)).toBeInTheDocument();
  });

  test('asks the player to choose a level when they have none', async () => {
    getRanking.mockResolvedValue({ level: null, top: [], me: null, hasSessionsInLevel: false });
    openRankingTab();

    expect(await screen.findByText(rankingTexts.noLevel)).toBeInTheDocument();
    expect(screen.getByRole('combobox')).toHaveValue('');
    expect(screen.getByRole('option', { name: rankingTexts.chooseLevel })).toBeDisabled();
  });

  test('shows an error without breaking the panel when the ranking cannot load', async () => {
    getRanking.mockRejectedValue(new Error('Sin conexión'));
    openRankingTab();

    expect(await screen.findByText(rankingTexts.loadError)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: texts.tabs.history }));
    expect(await screen.findByText(texts.empty)).toBeInTheDocument();
  });
});
