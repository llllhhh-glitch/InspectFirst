#!/bin/bash
set -u
script_dir="$(cd "$(dirname "$0")" && pwd)"
echo 'InspectFirst — initialize accounts (no MySQL reset)'
/usr/bin/python3 "$script_dir/setup-identity.py"
result=$?
echo
read -r -p 'Press Enter to close. '
exit "$result"
