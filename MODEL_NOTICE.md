# Embedded ASR model notice

GiVa HiAssist 1.0 is configured to embed `model-la13.onnx` and `tokens.txt` from the public `mobilebytesensei/betterflow-sravaani-streaming-onnx` conversion of `ARTPARK-IISc/SraVaani-0.5-live`.

- Underlying model authors: ARTPARK / Indian Institute of Science (IISc), Bengaluru.
- Conversion: ONNX opset 17, MatMul-only int8, sherpa-onnx metadata.
- No fine-tuning or distillation is claimed by the conversion repository.
- Upstream and conversion repository identify the license as MIT.
- HiAssist uses sherpa-onnx as the on-device inference runtime.

Model files are fetched at build time so they are included in the generated APK but need not be duplicated inside the source ZIP.
