#!/bin/bash
set -u
script_dir="$(cd "$(dirname "$0")" && pwd)"
/usr/bin/python3 "$script_dir/upgrade-backend.py"
result=$?
echo
read -r -p 'Press Enter to close. '
exit "$result"
