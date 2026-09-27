#!/usr/bin/env bash
# Pictures of real screens, drawn on the computer: scripts/shots.sh home,meds,history  → app/build/shots/*.png
# SHOTS_ROLE=helper for the helper's phone.
cd "$(dirname "$0")/.." && gradle --console=plain -q :app:testDebugUnitTest --tests com.suryaprakash.medlog.Shots \
  -Dshots="${1:-}" -Dshots.role="${SHOTS_ROLE:-self}" ${SHOTS_TALL:+-Dshots.tall=1} "${@:2}"
