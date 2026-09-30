"""
Prepara la publicación de una versión de Scorpk Asistente:
  1. copia el APK de release firmado a /releases con el nombre de la versión,
  2. calcula su SHA-256 y tamaño,
  3. escribe public/android/latest.json en el proyecto web (lo que consulta la app para actualizarse).

Uso (desde la raíz del proyecto Android, tras `.\\gradlew assembleRelease`):
  python scripts/release_android.py --notes "Novedad 1" --notes "Novedad 2" [--min-supported 1]

La versión (versionCode / versionName) se lee del propio APK, así que no hay que escribirla dos veces.
Después: crea en GitHub el release `android-v<versión>` de ScorpkID/scorpk-web con el APK de /releases
y solo entonces sube el cambio de latest.json a `main`.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import date

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk")
WEB_JSON = os.path.join(ROOT, "PAGINAOFICIALSCORPK", "scorpk-web", "public", "android", "latest.json")
REPO = "ScorpkID/scorpk-web"


def find_aapt2() -> str:
    sdk = os.environ.get("ANDROID_HOME") or os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk")
    tools = sorted(os.listdir(os.path.join(sdk, "build-tools")), key=lambda v: [int(p) for p in re.findall(r"\d+", v)])
    return os.path.join(sdk, "build-tools", tools[-1], "aapt2.exe")


def read_version(apk: str) -> tuple[int, str]:
    out = subprocess.run([find_aapt2(), "dump", "badging", apk], capture_output=True, text=True).stdout
    match = re.search(r"versionCode='(\d+)' versionName='([^']+)'", out)
    if not match:
        sys.exit("No pude leer la versión del APK.")
    if "application-debuggable" in out:
        sys.exit("El APK es depurable: no lo publiques. Compila con assembleRelease.")
    return int(match.group(1)), match.group(2)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--notes", action="append", default=[], help="Novedad (repetible)")
    parser.add_argument("--min-supported", type=int, default=None,
                        help="versionCode mínimo (por defecto, el de esta versión: toda actualización es obligatoria)")
    args = parser.parse_args()

    if not os.path.exists(APK):
        sys.exit(f"No existe {APK}. Ejecuta primero: .\\gradlew assembleRelease")

    code, name = read_version(APK)
    target = os.path.join(ROOT, "releases", f"scorpk-assistant-{name}.apk")
    os.makedirs(os.path.dirname(target), exist_ok=True)
    shutil.copyfile(APK, target)

    sha = hashlib.sha256(open(target, "rb").read()).hexdigest()
    latest = {
        "versionCode": code,
        "versionName": name,
        "minSupportedVersionCode": args.min_supported if args.min_supported is not None else code,
        "apkUrl": f"https://github.com/{REPO}/releases/download/android-v{name}/scorpk-assistant-{name}.apk",
        "sha256": sha,
        "sizeBytes": os.path.getsize(target),
        "releasedAt": date.today().isoformat(),
        "notes": args.notes,
    }
    with open(WEB_JSON, "w", encoding="utf-8") as f:
        json.dump(latest, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print(f"Versión      : {name} ({code})")
    print(f"APK          : {target}")
    print(f"SHA-256      : {sha}")
    print(f"latest.json  : {WEB_JSON}")
    print(f"Siguiente    : crea el release 'android-v{name}' en https://github.com/{REPO}/releases/new")
    print("               adjunta el APK de /releases, publícalo, y luego sube latest.json a main.")


if __name__ == "__main__":
    main()
