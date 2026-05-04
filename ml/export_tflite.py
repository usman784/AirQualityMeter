from __future__ import annotations

import argparse
import json
from pathlib import Path

import joblib
import numpy as np
import tensorflow as tf
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import train_test_split

from common import load_and_prepare_dataset


def metrics(y_true: np.ndarray, y_pred: np.ndarray) -> dict[str, float]:
    rmse = float(np.sqrt(mean_squared_error(y_true, y_pred)))
    return {
        "mae": float(mean_absolute_error(y_true, y_pred)),
        "rmse": rmse,
        "r2": float(r2_score(y_true, y_pred)),
    }


def train_tflite_ready_model(
    dataset_path: Path,
    output_tflite: Path,
    output_norm_json: Path,
    output_metrics_json: Path,
    teacher_model_path: Path | None = None,
    epochs: int = 200,
) -> None:
    x, y, resolved = load_and_prepare_dataset(dataset_path)
    x_train, x_test, y_train, y_test = train_test_split(
        x.to_numpy(dtype=np.float32),
        y.to_numpy(dtype=np.float32),
        test_size=0.20,
        random_state=42,
    )

    teacher = None
    if teacher_model_path is not None and teacher_model_path.exists():
        teacher = joblib.load(teacher_model_path)

    mean = x_train.mean(axis=0)
    std = x_train.std(axis=0)
    std = np.where(std == 0, 1.0, std)
    x_train_norm = (x_train - mean) / std
    x_test_norm = (x_test - mean) / std

    if teacher is not None:
        y_train_fit = teacher.predict(x_train).astype(np.float32)
        distillation_mode = "teacher_distillation"
    else:
        y_train_fit = y_train
        distillation_mode = "direct_supervised"

    model = tf.keras.Sequential(
        [
            tf.keras.layers.Input(shape=(4,), name="aqi_features"),
            tf.keras.layers.Dense(64, activation="relu"),
            tf.keras.layers.Dense(32, activation="relu"),
            tf.keras.layers.Dense(1, name="aqi_output"),
        ]
    )
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss="mse",
        metrics=["mae"],
    )

    early_stopping = tf.keras.callbacks.EarlyStopping(
        monitor="val_loss",
        patience=15,
        restore_best_weights=True,
    )

    model.fit(
        x_train_norm,
        y_train_fit,
        validation_split=0.2,
        epochs=epochs,
        batch_size=64,
        callbacks=[early_stopping],
        verbose=1,
    )

    y_pred = model.predict(x_test_norm, verbose=0).reshape(-1)
    eval_metrics = metrics(y_test, y_pred)

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    output_tflite.parent.mkdir(parents=True, exist_ok=True)
    output_tflite.write_bytes(tflite_model)

    output_norm_json.parent.mkdir(parents=True, exist_ok=True)
    norm_payload = {
        "features": ["temp", "humidity", "wind", "pm25"],
        "mean": mean.tolist(),
        "std": std.tolist(),
        "resolved_columns": {
            "temp": resolved.temp,
            "humidity": resolved.humidity,
            "wind": resolved.wind,
            "pm25": resolved.pm25,
            "target": resolved.target,
        },
    }
    output_norm_json.write_text(json.dumps(norm_payload, indent=2), encoding="utf-8")

    output_metrics_json.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "mode": distillation_mode,
        "teacher_model_used": str(teacher_model_path) if teacher_model_path else "",
        "test_metrics": eval_metrics,
    }
    output_metrics_json.write_text(json.dumps(payload, indent=2), encoding="utf-8")

    print("TFLite export complete.")
    print(f"Saved TFLite model to: {output_tflite}")
    print(f"Saved norm metadata to: {output_norm_json}")
    print(f"Saved deployment metrics to: {output_metrics_json}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Train and export AQI LiteRT/TFLite model for Android."
    )
    parser.add_argument(
        "--dataset",
        type=Path,
        required=True,
        help="Path to CSV dataset.",
    )
    parser.add_argument(
        "--teacher-model",
        type=Path,
        default=None,
        help="Optional joblib model from train_models.py for distillation.",
    )
    parser.add_argument(
        "--output-tflite",
        type=Path,
        default=Path("app") / "src" / "main" / "assets" / "aqi_model.tflite",
        help="Output path for generated aqi_model.tflite",
    )
    parser.add_argument(
        "--output-norm",
        type=Path,
        default=Path("app") / "src" / "main" / "assets" / "aqi_model_norm.json",
        help="Output path for normalization JSON used by Android app.",
    )
    parser.add_argument(
        "--output-metrics",
        type=Path,
        default=Path("ml") / "artifacts" / "tflite_metrics.json",
        help="Output path for TFLite model evaluation metrics JSON.",
    )
    parser.add_argument(
        "--epochs",
        type=int,
        default=200,
        help="Maximum training epochs for the deployable neural model.",
    )
    args = parser.parse_args()

    train_tflite_ready_model(
        dataset_path=args.dataset,
        output_tflite=args.output_tflite,
        output_norm_json=args.output_norm,
        output_metrics_json=args.output_metrics,
        teacher_model_path=args.teacher_model,
        epochs=args.epochs,
    )


if __name__ == "__main__":
    main()
