#!/bin/sh
# Inject runtime configuration into the frontend
# API_BASE_URL env var sets the backend URL (e.g. https://api.mixer.example.com/api)
CONFIG_FILE="/app/dist/config.js"
echo "window.__MIXER_CONFIG__ = { apiBase: \"${API_BASE_URL:-/api}\" };" > "$CONFIG_FILE"
exec serve -s dist -l 3000
