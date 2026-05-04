from __future__ import annotations

import argparse
import json
from pathlib import Path

import joblib
import numpy as np
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from common import load_and_prepare_dataset

try:
    from xgboost import XGBRegressor

    HAS_XGBOOST = True
except Exception:
    HAS_XGBOOST = False
    XGBRegressor = None  # type: ignore


def metrics(y_true: np.ndarray, y_pred: np.ndarray) -> dict[str, float]:
    rmse = float(np.sqrt(mean_squared_error(y_true, y_pred)))
    return {
        "mae": float(mean_absolute_error(y_true, y_pred)),
        "rmse": rmse,
        "r2": float(r2_score(y_true, y_pred)),
    }


def train_and_evaluate_models(dataset_path: Path, output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    x, y, resolved = load_and_prepare_dataset(dataset_path)

    x_train, x_temp, y_train, y_temp = train_test_split(
        x, y, test_size=0.30, random_state=42
    )
    x_val, x_test, y_val, y_test = train_test_split(
        x_temp, y_temp, test_size=0.50, random_state=42
    )

    models: dict[str, Pipeline] = {
        "random_forest": Pipeline(
            steps=[
                ("scaler", StandardScaler()),
                (
                    "model",
                    RandomForestRegressor(
                        n_estimators=500,
                        max_depth=None,
                        min_samples_split=2,
                        random_state=42,
                        n_jobs=-1,
                    ),
                ),
            ]
        )
    }

    if HAS_XGBOOST:
        models["xgboost"] = Pipeline(
            steps=[
                ("scaler", StandardScaler()),
                (
                    "model",
                    XGBRegressor(
                        n_estimators=600,
                        learning_rate=0.03,
                        max_depth=8,
                        subsample=0.85,
                        colsample_bytree=0.85,
                        reg_alpha=0.0,
                        reg_lambda=1.0,
                        random_state=42,
                        objective="reg:squarederror",
                        n_jobs=4,
                    ),
                ),
            ]
        )

    report: dict[str, dict[str, float]] = {}
    best_name: str | None = None
    best_val_rmse = float("inf")
    best_model: Pipeline | None = None

    for name, model in models.items():
        model.fit(x_train, y_train)
        y_val_pred = model.predict(x_val)
        val_metrics = metrics(y_val.to_numpy(), y_val_pred)
        report[f"{name}_val"] = val_metrics

        if val_metrics["rmse"] < best_val_rmse:
            best_val_rmse = val_metrics["rmse"]
            best_name = name
            best_model = model

    if best_model is None or best_name is None:
        raise RuntimeError("No model was trained successfully.")

    y_test_pred = best_model.predict(x_test)
    report["best_model_test"] = metrics(y_test.to_numpy(), y_test_pred)
    report["best_model_name"] = {"name": best_name}
    report["rows"] = {"count": float(len(x))}

    model_path = output_dir / "best_model.joblib"
    joblib.dump(best_model, model_path)

    # Save feature normalization metadata for Android (used by TFLitePredictor).
    mean = x_train.mean().to_list()
    std = [float(s if s != 0 else 1.0) for s in x_train.std().to_list()]
    norm = {
        "features": ["temp", "humidity", "wind", "pm25"],
        "mean": mean,
        "std": std,
        "resolved_columns": {
            "temp": resolved.temp,
            "humidity": resolved.humidity,
            "wind": resolved.wind,
            "pm25": resolved.pm25,
            "target": resolved.target,
        },
    }
    with (output_dir / "aqi_model_norm.json").open("w", encoding="utf-8") as f:
        json.dump(norm, f, indent=2)

    with (output_dir / "metrics.json").open("w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)

    print("Training complete.")
    print(f"Best model: {best_name}")
    print(f"Saved model to: {model_path}")
    print(f"Saved metrics to: {output_dir / 'metrics.json'}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Train RandomForest/XGBoost AQI models and export evaluation artifacts."
    )
    parser.add_argument(
        "--dataset",
        type=Path,
        required=True,
        help="Path to CSV dataset (must contain temp/humidity/wind/pm25/aqi-like columns).",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("ml") / "artifacts",
        help="Where to save model + metrics artifacts.",
    )
    args = parser.parse_args()
    train_and_evaluate_models(args.dataset, args.output_dir)


if __name__ == "__main__":
    main()
