import { registerHooks } from 'node:module';

// El dominio del tueste vive en la web y se escribió para el bundler de React: sus imports no llevan
// extensión y su package.json no declara "type": "module". Estos hooks le dan a Node lo que el bundler
// resuelve solo, así el simulador usa los mismos archivos sin copiarlos.
const DOMAIN_URL = new URL('../../toasted_vr_frontend/src/domain/roasting/', import.meta.url).href;

const isDomainUrl = (url) => typeof url === 'string' && url.startsWith(DOMAIN_URL);
const hasExtension = (specifier) => /\.[a-z0-9]+$/i.test(specifier.split('/').pop());

registerHooks({
  resolve(specifier, context, nextResolve) {
    const needsExtension = isDomainUrl(context.parentURL) && specifier.startsWith('.') && !hasExtension(specifier);
    const resolved = nextResolve(needsExtension ? `${specifier}.js` : specifier, context);
    return isDomainUrl(resolved.url) ? { ...resolved, format: 'module' } : resolved;
  },
});
