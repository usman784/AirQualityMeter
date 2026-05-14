# AQI ML Pipeline (FYP)

This folder contains the complete ML workflow used in this project:

1. Build training data from city CSV files.
2. Train and evaluate RandomForest / XGBoost teacher models.
3. Export LiteRT (TensorFlow Lite) model for Android deployment.

## Dataset Source

Kaggle dataset used:

https://www.kaggle.com/datasets/hajramohsin/pakistan-air-quality-pollutant-concentrations

## 1) Install

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r ml/requirements.txt
```

## 2) Prepare Training CSV (from city files)

If city-level CSV files are available (for example in `app/src/main/assets`), create one merged training file:

```bash
.venv/bin/python - <<'PY'
from pathlib import Path
import pandas as pd
import numpy as np

assets = Path('app/src/main/assets')
files = sorted(assets.glob('*_complete_data_july_to_dec_2024.csv'))
frames = []
for f in files:
    frames.append(pd.read_csv(f))
raw = pd.concat(frames, ignore_index=True)

x = pd.DataFrame({
    'temp': pd.to_numeric(raw['temperature_2m'], errors='coerce'),
    'humidity': pd.to_numeric(raw['relative_humidity_2m'], errors='coerce'),
    'wind': pd.to_numeric(raw['wind_speed_10m'], errors='coerce'),
    'pm25': pd.to_numeric(raw['components_pm2_5'], errors='coerce'),
})

# PM2.5 -> AQI (US EPA breakpoints)
bps = [
    (0.0, 12.0, 0, 50),
    (12.1, 35.4, 51, 100),
    (35.5, 55.4, 101, 150),
    (55.5, 150.4, 151, 200),
    (150.5, 250.4, 201, 300),
    (250.5, 350.4, 301, 400),
    (350.5, 500.4, 401, 500),
]
pm = x['pm25'].to_numpy(dtype=float)
aqi = np.full(pm.shape, np.nan, dtype=float)
for c_lo, c_hi, i_lo, i_hi in bps:
    mask = (pm >= c_lo) & (pm <= c_hi)
    aqi[mask] = ((i_hi - i_lo) / (c_hi - c_lo)) * (pm[mask] - c_lo) + i_lo
aqi[pm > 500.4] = 500.0
aqi[pm < 0] = 0.0

out = x.copy()
out['aqi'] = aqi
out = out.replace([np.inf, -np.inf], np.nan).dropna().reset_index(drop=True)
out = out[(out['humidity'] >= 0) & (out['humidity'] <= 100)]
out = out[(out['wind'] >= 0) & (out['pm25'] >= 0)]
out = out[(out['aqi'] >= 0) & (out['aqi'] <= 500)]
out.to_csv('ml/artifacts/pakistan_aqi_training.csv', index=False)
print('saved ml/artifacts/pakistan_aqi_training.csv rows=', len(out))
PY
```

If you already have a merged file, skip this step and use it directly:

`ml/artifacts/pakistan_aqi_training.csv`

## 3) Train Teacher Model (RF/XGBoost)

```bash
.venv/bin/python ml/train_models.py \
  --dataset ml/artifacts/pakistan_aqi_training.csv \
  --output-dir ml/artifacts
```

Outputs:

- `ml/artifacts/best_model.joblib`
- `ml/artifacts/metrics.json`
- `ml/artifacts/aqi_model_norm.json`

## 4) Export LiteRT / TFLite for Android

```bash
TF_USE_LEGACY_KERAS=1 .venv/bin/python ml/export_tflite.py \
  --dataset ml/artifacts/pakistan_aqi_training.csv \
  --teacher-model ml/artifacts/best_model.joblib \
  --output-tflite app/src/main/assets/aqi_model.tflite \
  --output-norm app/src/main/assets/aqi_model_norm.json \
  --output-metrics ml/artifacts/tflite_metrics.json
```

Note:

- `TF_USE_LEGACY_KERAS=1` avoids a TensorFlow 2.16 + Keras 3 conversion issue on some environments.
- If `--teacher-model` is omitted, export runs in direct supervised mode.

## Current Artifacts (Generated)

- `app/src/main/assets/aqi_model.tflite`
- `app/src/main/assets/aqi_model_norm.json`
- `ml/artifacts/metrics.json`
- `ml/artifacts/tflite_metrics.json`

## Dataset Column Requirements

The scripts auto-detect common names for:

- Temperature: `temp`, `temperature`, `t2m`, `temp_c`
- Humidity: `humidity`, `rh`, `relative_humidity`
- Wind: `wind`, `wind_speed`, `windspeed`, `ws`, `wind_mps`
- PM2.5: `pm25`, `pm2_5`, `pm2.5`, `pm_25`
- Target AQI: `aqi`, `aqi_value`, `air_quality_index`
