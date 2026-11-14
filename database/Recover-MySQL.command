#!/bin/bash
set -u
recovery_dir="$(cd "$(dirname "$0")" && pwd)"
echo 'InspectFirst — preserve-data MySQL recovery'
echo 'macOS will first request your COMPUTER administrator password.'
echo 'Then you will choose a NEW MySQL root password locally.'
echo 'Neither password is displayed as you type. No password needs to be sent to chat.'
echo
sudo /usr/bin/python3 "$recovery_dir/recover-mysql-access.py"
recovery_status=$?
echo
if [[ "$recovery_status" == 0 ]]; then
  echo 'Finished. Tell Codex the recovery completed; do not send your new password.'
else
  echo 'Recovery stopped. Tell Codex the last status/error line, without any password.'
fi
read -r -p 'Press Enter to close this window. '
exit "$recovery_status"
