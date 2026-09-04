# master-repo-sih-26

MARK-V Intelligent Dead Reckoning — SIH 2026 submission.

This repository contains the full project as submitted:

- pp/ — Android / Kotlin / Jetpack Compose application with the IDR-V1
  inertial dead-reckoning pipeline, EKF estimator, GNSS quality monitor,
  map matching and offline OSM road network.
- .codex-ml-codes-review/ — Python training pipeline, dataset diagnostics,
  blackout evaluation scripts and the IDR-V1 model weights.

The Categorised IOVNB Dataset/ folder (414 MB, 72 sessions) is excluded
from version control. Place it at the workspace root to reproduce training.

Built and tested: 96 unit tests pass, ssembleDebug succeeds.
