#!/bin/sh
set -eu

: "${REACT_APP_API_BASE_URL:?Set REACT_APP_API_BASE_URL}"

grep -rl '__RUNTIME_API_BASE_URL__' /usr/share/nginx/html \
  | xargs -r sed -i "s|__RUNTIME_API_BASE_URL__|${REACT_APP_API_BASE_URL}|g"

exec nginx -g "daemon off;"
