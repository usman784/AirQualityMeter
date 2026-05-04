# Model Assets

Place your exported LiteRT / TensorFlow Lite model here:

- `aqi_model.tflite`
- `aqi_model_norm.json` (already included as default scaffold)

The app loads `aqi_model.tflite` via `TFLitePredictor`.
If the model file is missing, the app automatically falls back to `AqiMlPredictor`.
