import fs from 'fs';
import path from 'path';
import { KNOWN_EMAIL_PROVIDERS, MAX_EMAIL_TYPO_DISTANCE } from './validation';

// El registro revisa el dominio del correo en el navegador (aviso inmediato) y
// en el servidor (app.email-domain en application.yml). Este test falla si las
// dos listas dejan de coincidir.
const applicationYaml = fs.readFileSync(
  path.resolve(__dirname, '../../../Toasted_VR/src/main/resources/application.yml'),
  'utf8'
);

const readBackendDefault = (property) => {
  const match = applicationYaml.match(new RegExp(`^\\s*${property}:\\s*\\$\\{[A-Z_]+:([^}]+)\\}`, 'm'));
  if (!match) throw new Error(`No se encontró app.email-domain.${property} en application.yml`);
  return match[1];
};

test('known-providers coincide con KNOWN_EMAIL_PROVIDERS', () => {
  expect(readBackendDefault('known-providers').split(',')).toEqual(KNOWN_EMAIL_PROVIDERS);
});

test('max-typo-distance coincide con MAX_EMAIL_TYPO_DISTANCE', () => {
  expect(Number(readBackendDefault('max-typo-distance'))).toBe(MAX_EMAIL_TYPO_DISTANCE);
});
