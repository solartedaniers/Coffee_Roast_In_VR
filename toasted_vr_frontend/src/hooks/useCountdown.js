import { useEffect, useState } from 'react';

// Cuenta regresiva en segundos. Devuelve el valor actual y una función para
// reiniciarla con otro valor (0 la detiene).
export function useCountdown(initialSeconds) {
  const [secondsLeft, setSecondsLeft] = useState(initialSeconds);

  useEffect(() => {
    if (secondsLeft <= 0) {
      return undefined;
    }

    const timerId = window.setInterval(() => {
      setSecondsLeft((currentValue) => (currentValue > 0 ? currentValue - 1 : 0));
    }, 1000);

    return () => window.clearInterval(timerId);
  }, [secondsLeft]);

  return [secondsLeft, setSecondsLeft];
}

export const formatCountdown = (totalSeconds) => {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
};
