from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List

import pandas as pd


@dataclass(frozen=True)
class DatasetColumns:
    temp: str
    humidity: str
    wind: str
    pm25: str
    target: str


FEATURE_ALIASES: Dict[str, List[str]] = {
    "temp": ["temp", "temperature", "t2m", "temp_c"],
    "humidity": ["humidity", "rh", "relative_humidity"],
    "wind": ["wind", "wind_speed", "windspeed", "ws", "wind_mps"],
    "pm25": ["pm25", "pm2_5", "pm2.5", "pm_25"],
    "target": ["aqi", "aqi_value", "air_quality_index"],
}


def _resolve_column(columns: List[str], aliases: List[str]) -> str:
    normalized = {c.lower().strip(): c for c in columns}
    for alias in aliases:
        hit = normalized.get(alias.lower().strip())
        if hit is not None:
            return hit
    raise ValueError(
        f"Could not resolve required column from aliases={aliases}. "
        f"Available columns={columns}"
    )


def load_and_prepare_dataset(csv_path: Path) -> tuple[pd.DataFrame, pd.Series, DatasetColumns]:
    if not csv_path.exists():
        raise FileNotFoundError(f"Dataset file not found: {csv_path}")

    df = pd.read_csv(csv_path)
    columns = df.columns.tolist()

    resolved = DatasetColumns(
        temp=_resolve_column(columns, FEATURE_ALIASES["temp"]),
        humidity=_resolve_column(columns, FEATURE_ALIASES["humidity"]),
        wind=_resolve_column(columns, FEATURE_ALIASES["wind"]),
        pm25=_resolve_column(columns, FEATURE_ALIASES["pm25"]),
        target=_resolve_column(columns, FEATURE_ALIASES["target"]),
    )

    selected = df[[resolved.temp, resolved.humidity, resolved.wind, resolved.pm25, resolved.target]].copy()
    selected = selected.apply(pd.to_numeric, errors="coerce").dropna().reset_index(drop=True)
    selected = selected[(selected[resolved.target] >= 0) & (selected[resolved.target] <= 500)]

    x = selected[[resolved.temp, resolved.humidity, resolved.wind, resolved.pm25]].rename(
        columns={
            resolved.temp: "temp",
            resolved.humidity: "humidity",
            resolved.wind: "wind",
            resolved.pm25: "pm25",
        }
    )
    y = selected[resolved.target]
    return x, y, resolved
