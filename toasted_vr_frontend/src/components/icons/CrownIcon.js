import React from 'react';

// Icono de corona (ranking). El dorado lo pone el CSS con --color-crown-gold.
export default function CrownIcon(props) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false" {...props}>
      <path
        fill="currentColor"
        d="M3 7.5 7.6 11 12 4.5 16.4 11 21 7.5 19.2 17H4.8L3 7.5Zm1.9 11h14.2v2H4.9v-2Z"
      />
    </svg>
  );
}
