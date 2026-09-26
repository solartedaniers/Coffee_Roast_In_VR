import { fireEvent, render, screen } from '@testing-library/react';
import PlayerProgressButtons from './PlayerProgressButtons';
import esTexts from '../../locals/es.json';
import { getRanking, getSessionHistory, getSessionSummary } from '../../services/progressService';

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
const player = { id: 7, username: 'anaTorres', role: 'PLAYER' };

const historyButton = () => screen.getByRole('button', { name: texts.historyTitle });
const rankingButton = () => screen.queryByRole('button', { name: texts.rankingTitle });

describe('PlayerProgressButtons', () => {
  beforeEach(() => {
    getSessionSummary.mockResolvedValue({ bestScore: null, averageScore: null, totalSessions: 0 });
    getSessionHistory.mockResolvedValue({ content: [], number: 0, totalPages: 0, totalElements: 0 });
    getRanking.mockResolvedValue({ level: 'INTERMEDIATE', top: [], me: null, hasSessionsInLevel: false });
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  test('shows a clock and a crown with their aria-label and tooltip from es.json, and no window yet', () => {
    render(<PlayerProgressButtons texts={texts} currentUser={player} />);

    expect(historyButton()).toHaveAttribute('title', 'Historial');
    expect(rankingButton()).toHaveAttribute('title', 'Ranking');
    expect(rankingButton()).toHaveClass('player-progress-btn-crown');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
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

  test('hides the crown from administrators, who are not ranked', () => {
    render(<PlayerProgressButtons texts={texts} currentUser={{ ...player, role: 'ADMIN' }} />);

    expect(historyButton()).toBeInTheDocument();
    expect(rankingButton()).not.toBeInTheDocument();
  });
});
