import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import PlayerProgressButtons from './PlayerProgressButtons';
import esTexts from '../../locals/es.json';
import {
  getRanking,
  getRankingChanges,
  getSessionHistory,
  getSessionSummary,
  markRankingSeen,
} from '../../services/progressService';
import { ROAST_SESSION_SAVED_EVENT } from '../../services/simulationService';

jest.mock('../../services/progressService', () => ({
  getRanking: jest.fn(),
  getRankingChanges: jest.fn(),
  markRankingSeen: jest.fn(),
  getSessionHistory: jest.fn(),
  getSessionSummary: jest.fn(),
  getSessionDetail: jest.fn(),
}));

jest.mock('../../services/simulationService', () => ({
  ...jest.requireActual('../../services/simulationService'),
  getRoastingFeedback: jest.fn(),
}));

const texts = esTexts.simulation.dataHistory;
const player = { id: 7, username: 'anaTorres', role: 'PLAYER' };
const changes = (hasChanges) => ({ level: 'INTERMEDIATE', hasChanges, pollIntervalSeconds: 60 });

const historyButton = () => screen.getByRole('button', { name: texts.historyTitle });
const rankingButton = () => screen.queryByRole('button', { name: texts.rankingTitle });
const dot = () => screen.queryByTestId('ranking-changes-dot');
// Deja que termine la consulta pendiente antes de mover el reloj falso.
const flushPendingCheck = () => act(async () => {
  await Promise.resolve();
});

let hidden = false;

describe('PlayerProgressButtons', () => {
  beforeAll(() => {
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden });
  });

  beforeEach(() => {
    hidden = false;
    getSessionSummary.mockResolvedValue({ bestScore: null, averageScore: null, totalSessions: 0 });
    getSessionHistory.mockResolvedValue({ content: [], number: 0, totalPages: 0, totalElements: 0 });
    getRanking.mockResolvedValue({ level: 'INTERMEDIATE', top: [], me: null, hasSessionsInLevel: false, signature: 'sig-1' });
    getRankingChanges.mockResolvedValue(changes(false));
    markRankingSeen.mockResolvedValue(undefined);
  });

  afterEach(() => {
    jest.clearAllMocks();
    jest.useRealTimers();
  });

  test('shows a clock and a crown with their aria-label and tooltip from es.json, and no window yet', async () => {
    render(<PlayerProgressButtons texts={texts} currentUser={player} />);

    expect(historyButton()).toHaveAttribute('title', 'Historial');
    expect(rankingButton()).toHaveAttribute('title', 'Ranking');
    expect(rankingButton()).toHaveClass('player-progress-btn-crown');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await waitFor(() => expect(getRankingChanges).toHaveBeenCalledTimes(1));
    expect(dot()).not.toBeInTheDocument();
    expect(getSessionHistory).not.toHaveBeenCalled();
    expect(getRanking).not.toHaveBeenCalled();
  });

  test('the clock opens a "Historial" window with only the history and no tabs', async () => {
    render(<PlayerProgressButtons texts={texts} currentUser={player} />);

    fireEvent.click(historyButton());

    expect(await screen.findByRole('dialog', { name: 'Historial' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Historial' })).toBeInTheDocument();
    expect(await screen.findByText(texts.empty)).toBeInTheDocument();
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
    expect(screen.queryByRole('tab')).not.toBeInTheDocument();
    expect(screen.queryByText(texts.ranking.levelLabel)).not.toBeInTheDocument();
    expect(getRanking).not.toHaveBeenCalled();
  });

  test('the crown opens a "Ranking" window with only the ranking and no tabs', async () => {
    render(<PlayerProgressButtons texts={texts} currentUser={player} />);

    fireEvent.click(rankingButton());

    expect(await screen.findByRole('dialog', { name: 'Ranking' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Ranking' })).toBeInTheDocument();
    expect(await screen.findByText(texts.ranking.noSessionsInLevel)).toBeInTheDocument();
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
    expect(screen.queryByRole('tab')).not.toBeInTheDocument();
    expect(screen.queryByText(texts.summary.best)).not.toBeInTheDocument();
    expect(getSessionHistory).not.toHaveBeenCalled();
  });

  test('closes the window', async () => {
    render(<PlayerProgressButtons texts={texts} currentUser={player} />);
    fireEvent.click(historyButton());
    await screen.findByRole('dialog');

    fireEvent.click(screen.getByRole('button', { name: texts.close }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  test('hides the crown from administrators and never asks for ranking changes', async () => {
    render(<PlayerProgressButtons texts={texts} currentUser={{ ...player, role: 'ADMIN' }} />);

    expect(historyButton()).toBeInTheDocument();
    expect(rankingButton()).not.toBeInTheDocument();
    act(() => {
      window.dispatchEvent(new Event(ROAST_SESSION_SAVED_EVENT));
    });
    expect(getRankingChanges).not.toHaveBeenCalled();
  });

  test('shows a dot on the crown, described for screen readers, when the ranking changed', async () => {
    getRankingChanges.mockResolvedValue(changes(true));
    render(<PlayerProgressButtons texts={texts} currentUser={player} />);

    await waitFor(() => expect(dot()).toBeInTheDocument());
    expect(rankingButton()).toHaveAccessibleDescription(texts.ranking.changesNotice);
  });

  test('opening the ranking marks the shown ranking as seen and the dot disappears', async () => {
    getRankingChanges.mockResolvedValueOnce(changes(true)).mockResolvedValue(changes(false));
    render(<PlayerProgressButtons texts={texts} currentUser={player} />);
    await waitFor(() => expect(dot()).toBeInTheDocument());

    fireEvent.click(rankingButton());

    await waitFor(() => expect(markRankingSeen).toHaveBeenCalledWith('sig-1'));
    await waitFor(() => expect(dot()).not.toBeInTheDocument());
    expect(getRankingChanges).toHaveBeenCalledTimes(2);
  });

  test('keeps the dot when the ranking changed after it was shown', async () => {
    getRankingChanges.mockResolvedValue(changes(true));
    render(<PlayerProgressButtons texts={texts} currentUser={player} />);
    await waitFor(() => expect(dot()).toBeInTheDocument());

    fireEvent.click(rankingButton());

    await waitFor(() => expect(markRankingSeen).toHaveBeenCalledWith('sig-1'));
    await waitFor(() => expect(getRankingChanges).toHaveBeenCalledTimes(2));
    expect(dot()).toBeInTheDocument();
  });

  test('asks again after a roast is saved', async () => {
    render(<PlayerProgressButtons texts={texts} currentUser={player} />);
    await waitFor(() => expect(getRankingChanges).toHaveBeenCalledTimes(1));

    getRankingChanges.mockResolvedValue(changes(true));
    act(() => {
      window.dispatchEvent(new Event(ROAST_SESSION_SAVED_EVENT));
    });

    await waitFor(() => expect(dot()).toBeInTheDocument());
    expect(getRankingChanges).toHaveBeenCalledTimes(2);
  });

  test('polls every pollIntervalSeconds, pauses while the tab is hidden and asks again when it is visible', async () => {
    jest.useFakeTimers();
    render(<PlayerProgressButtons texts={texts} currentUser={player} />);
    await flushPendingCheck();
    expect(getRankingChanges).toHaveBeenCalledTimes(1);

    await act(async () => {
      jest.advanceTimersByTime(59_000);
    });
    expect(getRankingChanges).toHaveBeenCalledTimes(1);
    await act(async () => {
      jest.advanceTimersByTime(1_000);
    });
    expect(getRankingChanges).toHaveBeenCalledTimes(2);

    hidden = true;
    await act(async () => {
      jest.advanceTimersByTime(60_000);
    });
    await act(async () => {
      jest.advanceTimersByTime(120_000);
    });
    expect(getRankingChanges).toHaveBeenCalledTimes(2);

    hidden = false;
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
    expect(getRankingChanges).toHaveBeenCalledTimes(3);
  });

  test('stops polling when the bar is removed', async () => {
    jest.useFakeTimers();
    const { unmount } = render(<PlayerProgressButtons texts={texts} currentUser={player} />);
    await flushPendingCheck();

    unmount();
    await act(async () => {
      jest.advanceTimersByTime(600_000);
    });

    expect(getRankingChanges).toHaveBeenCalledTimes(1);
  });
});
