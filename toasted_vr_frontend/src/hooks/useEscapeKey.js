import { useEffect, useRef } from 'react';

// Pila de manejadores: Esc solo avisa al último que se activó, así una vista
// interna (por ejemplo el detalle de una sesión) responde antes que su ventana.
const handlers = [];

const handleKeyDown = (event) => {
  if (event.key !== 'Escape' || handlers.length === 0) return;
  handlers[handlers.length - 1].current();
};

export default function useEscapeKey(onEscape, enabled = true) {
  const handlerRef = useRef(onEscape);
  handlerRef.current = onEscape;

  useEffect(() => {
    if (!enabled) return undefined;
    if (handlers.length === 0) document.addEventListener('keydown', handleKeyDown);
    handlers.push(handlerRef);
    return () => {
      handlers.splice(handlers.indexOf(handlerRef), 1);
      if (handlers.length === 0) document.removeEventListener('keydown', handleKeyDown);
    };
  }, [enabled]);
}
