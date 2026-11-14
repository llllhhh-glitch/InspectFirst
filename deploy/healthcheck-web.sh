#!/bin/sh
set -eu
for path in / /api/auth/health /api/health /api/inspections/health /api/reports/health; do
    wget -q -T 5 -O /dev/null "http://127.0.0.1$path" || exit 1
done
