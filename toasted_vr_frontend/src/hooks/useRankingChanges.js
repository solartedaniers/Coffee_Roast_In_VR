import { useCallback, useEffect, useRef, useState } from 'react';
import { getRankingChanges } from '../services/progressService';
import { ROAST_SESSION_SAVED_EVENT } from '../services/simulationService';

// Punto de aviso de la corona (RF017): pregunta al servidor si el ranking del
// nivel del jugador cambió desde la última vez que lo vio. Consulta al montar
// (al entrar), después de guardar un tueste y cada pollIntervalSeconds (lo
// decide el servidor). Con la pestaña oculta no consulta; al volver a verla,
// consulta de inmediato. Corre con su propio temporizador, aparte del reloj
// de la simulación.
export function useRankingChanges(enabled) {
  const [hasChanges, setHasChanges] = useState(false);
  const timeoutRef = useRef(null);
  const pollSecondsRef = useRef(null);
  const activeRef = useRef(false);
  const runRef = useRef(0);

  const check = useCallback(async () => {
    clearTimeout(timeoutRef.current);
    if (!activeRef.current || document.hidden) return;

    // Si otra consulta empieza mientras esta espera, solo la última programa la siguiente.
    const run = ++runRef.current;
    try {
      const response = await getRankingChanges();
      if (!activeRef.current || run !== runRef.current) return;
      setHasChanges(response.hasChanges);
      pollSecondsRef.current = response.pollIntervalSeconds;
    } catch {
      // Sin conexión: se reintenta en la siguiente vuelta, al guardar o al volver a la pestaña.
    }
    if (activeRef.current && run === runRef.current && pollSecondsRef.current) {
      timeoutRef.current = setTimeout(check, pollSecondsRef.current * 1000);
    }
  }, []);

  useEffect(() => {
    if (!enabled) {
      setHasChanges(false);
      return undefined;
    }
    activeRef.current = true;
    const handleVisibility = () => {
      if (!document.hidden) check();
    };
    window.addEventListener(ROAST_SESSION_SAVED_EVENT, check);
    document.addEventListener('visibilitychange', handleVisibility);
    check();

    return () => {
      activeRef.current = false;
      clearTimeout(timeoutRef.current);
      window.removeEventListener(ROAST_SESSION_SAVED_EVENT, check);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [enabled, check]);

  return { hasChanges, refresh: check };
}
