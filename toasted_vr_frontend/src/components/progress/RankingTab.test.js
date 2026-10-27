import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import RankingTab from './RankingTab';
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

const openRankingTab = () => render(<RankingTab texts={texts} />);

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

  test('asks for the player level by default and shows position, username, best score and date', async () => {
    openRankingTab();

    expect(await screen.findByText('betoUser')).toBeInTheDocument();
    expect(getRanking).toHaveBeenCalledWith(null);
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
    expect(screen.getByRole('combobox')).toBeInTheDocument();
  });

  test('shows ▲, ▼ and "Nuevo" since the last visit, and nothing when the row did not move', async () => {
    const withMovement = (position, username, movement) => ({ ...entry(position, username, 90 - position), movement });
    getRanking.mockResolvedValue({
      level: 'INTERMEDIATE',
      top: [
        withMovement(1, 'carlaUser', { direction: 'NEW', places: 0 }),
        withMovement(2, 'viewerUser', { direction: 'UP', places: 1 }),
        withMovement(3, 'anaUser', { direction: 'DOWN', places: 2 }),
        withMovement(4, 'betoUser', { direction: 'SAME', places: 0 }),
      ],
      me: withMovement(2, 'viewerUser', { direction: 'UP', places: 1 }),
      hasSessionsInLevel: true,
      signature: 'sig',
    });
    openRankingTab();
    await screen.findByText('carlaUser');

    const rows = tableRows();
    expect(within(rows[1]).getByRole('img', { name: rankingTexts.movement.newLabel })).toHaveTextContent('Nuevo');
    expect(within(rows[1]).getByText('Nuevo')).toHaveClass('ranking-movement-new');
    expect(within(rows[2]).getByRole('img', { name: 'Subió 1 puesto' })).toHaveTextContent('▲ 1');
    expect(within(rows[2]).getByText('▲ 1')).toHaveClass('ranking-movement-up');
    expect(within(rows[3]).getByRole('img', { name: 'Bajó 2 puestos' })).toHaveTextContent('▼ 2');
    expect(within(rows[3]).getByText('▼ 2')).toHaveClass('ranking-movement-down');
    expect(within(rows[4]).queryByRole('img')).not.toBeInTheDocument();
  });

  test('shows no arrows on the first visit', async () => {
    openRankingTab();
    await screen.findByText('betoUser');

    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  test('shows the movement of the player row outside the top', async () => {
    const top = Array.from({ length: 10 }, (_, index) => ({ ...entry(index + 1, `rival${index}`, 99 - index), movement: null }));
    getRanking.mockResolvedValue({
      level: 'INTERMEDIATE',
      top,
      me: { ...entry(12, 'viewerUser', 50), movement: { direction: 'DOWN', places: 1 } },
      hasSessionsInLevel: true,
      signature: 'sig',
    });
    openRankingTab();
    await screen.findByText('rival0');

    expect(within(tableRows()[11]).getByRole('img', { name: 'Bajó 1 puesto' })).toBeInTheDocument();
  });

  test('passes every shown ranking to onLoaded, so it can be marked as seen', async () => {
    const onLoaded = jest.fn();
    render(<RankingTab texts={texts} onLoaded={onLoaded} />);
    await screen.findByText('betoUser');
    expect(onLoaded).toHaveBeenLastCalledWith(intermediateRanking);

    const advanced = { level: 'ADVANCED', top: [], me: null, hasSessionsInLevel: false, signature: 'sig-adv' };
    getRanking.mockResolvedValue(advanced);
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'ADVANCED' } });

    await waitFor(() => expect(onLoaded).toHaveBeenLastCalledWith(advanced));
    expect(onLoaded).toHaveBeenCalledTimes(2);
  });
});
