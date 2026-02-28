#!/bin/sh
# Inject runtime configuration into the frontend
# API_BASE_URL env var sets the backend URL (e.g. https://api.mixer.example.com/api)
CONFIG_FILE="/usr/share/nginx/html/config.js"
echo "window.__MIXER_CONFIG__ = { apiBase: \"${API_BASE_URL:-/api}\" };" > "$CONFIG_FILE"
exec nginx -g 'daemon off;'
