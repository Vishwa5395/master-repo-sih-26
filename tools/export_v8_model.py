"""Export the reviewed V8 checkpoint to the Android ONNX Runtime format."""

import argparse
import json
import shutil
import sys
import hashlib
from pathlib import Path

import torch


ROOT = Path(__file__).resolve().parents[1]
ML_REPOSITORY = ROOT / ".codex-ml-codes-review"
sys.path.insert(0, str(ML_REPOSITORY))

from src.models.hybrid_v8 import V8DeadReckoningModel  # noqa: E402


class AndroidV8Model(torch.nn.Module):
    """ONNX requires an ordered tensor output instead of the training dictionary."""

    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, imu, initial_speed_normalized):
        output = self.model(imu, initial_speed_normalized)
        return (
            output["speed"],
            output["speed_log_variance"],
            output["position"],
            output["position_log_variance"],
            output["heading_delta"],
            output["heading_delta_log_variance"],
            output["motion_logits"],
        )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        type=Path,
        default=ROOT / "app" / "src" / "main" / "assets" / "ml",
    )
    args = parser.parse_args()

    candidate_dir = ML_REPOSITORY / "models" / "candidates" / "v8_heading_delta_run1"
    checkpoint = torch.load(candidate_dir / "best_model.pt", map_location="cpu")
    model = V8DeadReckoningModel(input_channels=6, conv_dim=96, hidden_dim=128, dropout=0.15)
    model.load_state_dict(checkpoint["model_state_dict"])
    model.eval()

    args.output.mkdir(parents=True, exist_ok=True)
    export_model = AndroidV8Model(model)
    imu = torch.zeros(1, 20, 6, dtype=torch.float32)
    initial_speed = torch.zeros(1, dtype=torch.float32)
    torch.onnx.export(
        export_model,
        (imu, initial_speed),
        args.output / "v8_dead_reckoning.onnx",
        input_names=["imu", "initial_speed_normalized"],
        output_names=[
            "speed",
            "speed_log_variance",
            "position",
            "position_log_variance",
            "heading_delta",
            "heading_delta_log_variance",
            "motion_logits",
        ],
        dynamic_axes={"imu": {0: "batch"}, "initial_speed_normalized": {0: "batch"}},
        opset_version=17,
        dynamo=False,
    )
    shutil.copy2(candidate_dir / "normalization.json", args.output / "v8_normalization.json")
    report = json.loads((candidate_dir / "test_results.json").read_text(encoding="utf-8"))
    onnx_hash = hashlib.sha256((args.output / "v8_dead_reckoning.onnx").read_bytes()).hexdigest()
    (args.output / "v8_manifest.json").write_text(
        json.dumps(
            {
                "model": "V8 state-conditioned heading-delta",
                "input": {"imu": [20, 6], "sample_rate_hz": 10, "initial_speed": "normalized m/s"},
                "outputs": ["speed", "position", "heading_delta", "motion_logits"],
                "checkpoint_epoch": checkpoint["epoch"],
                "sha256": onnx_hash,
                "selection": "validation only; held-out test never used for selection",
                "held_out_test": {
                    "speed_mae_mps": report["speed_mae_mps"],
                    "position_rmse_m": report["position_rmse_m"],
                    "motion_accuracy": report["motion_accuracy"],
                    "trajectory_final_error_m": {horizon: values["mean_final_position_error_m"] for horizon, values in report["trajectory"].items()},
                },
                "deployment_status": "experimental: benchmark acceptance must be decided from held-out trajectory results",
            },
            indent=2,
        ),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
