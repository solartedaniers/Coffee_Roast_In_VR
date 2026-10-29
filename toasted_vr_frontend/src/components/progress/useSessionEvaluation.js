import { useEffect, useState } from 'react';
import { getSessionDetail } from '../../services/progressService';
import { evaluationPollIntervalMs, evaluationPollMaxAttempts } from '../../config/env';

const isPending = (session) => session?.evaluation?.status === 'PENDING';

// Mientras la IA califica la sesión en el servidor, vuelve a pedir el detalle cada cierto tiempo
// hasta tener la nota final. Después de varios intentos deja de preguntar y avisa (timedOut).
export default function useSessionEvaluation(initialSession) {
  const [refreshed, setRefreshed] = useState(null);
  const [timedOut, setTimedOut] = useState(false);
  const session = refreshed && refreshed.id === initialSession?.id ? refreshed : initialSession;
  const sessionId = session?.id;
  const pending = isPending(session);

  useEffect(() => {
    setTimedOut(false);
    if (!pending) return undefined;
    let cancelled = false;
    let attempts = 0;
    const timer = setInterval(async () => {
      attempts += 1;
      if (attempts >= evaluationPollMaxAttempts) {
        clearInterval(timer);
      }
      try {
        const fresh = await getSessionDetail(sessionId);
        if (!cancelled && !isPending(fresh)) {
          setRefreshed(fresh);
          return;
        }
      } catch (error) {
        // Un fallo de red no cambia nada: se reintenta en el siguiente intervalo.
      }
      if (!cancelled && attempts >= evaluationPollMaxAttempts) {
        setTimedOut(true);
      }
    }, evaluationPollIntervalMs);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [pending, sessionId]);

  return { session, timedOut };
}
