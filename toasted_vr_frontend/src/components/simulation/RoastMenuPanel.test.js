import { render, screen } from '@testing-library/react';
import RoastMenuPanel from './RoastMenuPanel';
import esTexts from '../../locals/es.json';
import { OPERATION_MODES } from '../../domain/roasting/RoastConstants';

// El historial y el ranking se abren desde la barra superior, no desde el menú.
test('the simulator menu no longer offers "Historial y ranking"', () => {
  render(
    <RoastMenuPanel
      texts={esTexts.simulation.controlPanel.menu}
      isOpen
      onClose={jest.fn()}
      operationMode={OPERATION_MODES.MANUAL}
      onOpenAutoControl={jest.fn()}
      onOpenManualControl={jest.fn()}
      onOpenSettings={jest.fn()}
      onOpenHelp={jest.fn()}
      onAbort={jest.fn()}
    />
  );

  expect(screen.getByRole('menuitem', { name: esTexts.simulation.controlPanel.menu.openGeneralSettings })).toBeInTheDocument();
  expect(screen.queryByRole('menuitem', { name: /historial/i })).not.toBeInTheDocument();
  expect(screen.queryByText('Historial y ranking')).not.toBeInTheDocument();
});
