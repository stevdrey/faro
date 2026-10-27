#!/usr/bin/env python3
"""Explicit small-model setup/lifecycle, isolated from the managed base runtime."""

import argparse
import copy
from contextlib import redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import re
import sys


HERE = Path(__file__).resolve().parent
BASELINE = HERE.parents[1] / "infra/whisper/baseline.py"


def small_runtime():
    spec = importlib.util.spec_from_file_location("faro_small_runtime", BASELINE)
    runtime = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(runtime)
    model = json.loads((HERE / "small-artifact.json").read_text())
    if (model["id"] != "small-multilingual" or model["file"] != "ggml-small.bin"
            or not re.fullmatch(r"[a-f0-9]{40}", model["revision"])
            or not re.fullmatch(r"[a-f0-9]{64}", model["sha256"])
            or type(model["size_bytes"]) is not int or model["size_bytes"] <= 0
            or model["url"] != "https://huggingface.co/ggerganov/whisper.cpp/resolve/"
               + model["revision"] + "/ggml-small.bin?download=true"):
        raise runtime.BaselineError("SMALL_MANIFEST_INVALID")
    base_config = runtime.manifest()
    runtime.manifest = lambda: {**copy.deepcopy(base_config), "model": copy.deepcopy(model)}
    runtime.MODEL_PATH = BASELINE.parent / "models/ggml-small.bin"
    runtime.CONTAINER = "faro-whisper-small"
    runtime.OWNER_LABEL = "io.github.stevdrey.faro.baseline=whisper-small"
    runtime.BASE_URL = "http://127.0.0.1:8086"
    original_args = runtime.docker_start_args

    def start_args(config, launch_token):
        args = original_args(config, launch_token)
        args[args.index("--publish") + 1] = "127.0.0.1:8086:8080"
        return args

    runtime.docker_start_args = start_args
    return runtime


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("setup-small", "verify-small", "start-small", "stop-small"))
    args = parser.parse_args()
    try:
        runtime = small_runtime()
        model = runtime.manifest()["model"]
        if args.command == "setup-small":
            if runtime.MODEL_PATH.exists() or runtime.MODEL_PATH.is_symlink():
                runtime.verify_model(runtime.MODEL_PATH, model)
            else:
                runtime.download_model(runtime.MODEL_PATH, model)
            runtime.MODEL_PATH.chmod(0o644)
        if args.command in ("setup-small", "verify-small"):
            runtime.verify_model(runtime.MODEL_PATH, model)
            print(f"Small verified: {model['size_bytes']} bytes; SHA-256 {model['sha256']}")
        elif args.command == "start-small":
            with redirect_stdout(io.StringIO()):
                runtime.start()
            print("Small server ready: http://127.0.0.1:8086; CPU, small-multilingual, Spanish (es).")
        else:
            runtime.stop()
        return 0
    except KeyboardInterrupt:
        return 130
    except Exception:
        print("Small command failed: LOCAL_MODEL_OPERATION_FAILED", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
