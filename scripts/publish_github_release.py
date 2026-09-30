"""
Crea (o completa) el release de GitHub de la versión indicada en public/android/latest.json y sube el APK.

Uso, después de `python scripts/release_android.py ...`:
  python scripts/publish_github_release.py

Usa la credencial de GitHub que Git Credential Manager ya tiene guardada (la misma del `git push`);
solo vive en memoria y nunca se imprime. Requiere permiso de escritura en ScorpkID/scorpk-web.
Después de este paso, sube el cambio de latest.json a `main`: la app solo verá la versión nueva
cuando el sitio la anuncie.
"""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB = os.path.join(ROOT, "PAGINAOFICIALSCORPK", "scorpk-web")
LATEST = os.path.join(WEB, "public", "android", "latest.json")
REPO = "ScorpkID/scorpk-web"


def credential() -> str:
    result = subprocess.run(
        ["git", "credential", "fill"], input="protocol=https\nhost=github.com\n\n",
        capture_output=True, text=True, cwd=WEB,
    )
    fields = dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)
    token = fields.get("password")
    if not token:
        sys.exit("No hay credencial de GitHub guardada.")
    return token


def api(token: str, method: str, url: str, body=None, data=None, content_type="application/json"):
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "scorpk-release",
    }
    payload = data if data is not None else (json.dumps(body).encode() if body is not None else None)
    if payload is not None:
        headers["Content-Type"] = content_type
    request = urllib.request.Request(url, data=payload, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=900) as response:
            return response.status, json.loads(response.read() or b"{}")
    except urllib.error.HTTPError as error:
        try:
            detail = json.loads(error.read()).get("message", "")
        except Exception:
            detail = ""
        return error.code, {"error": detail}


def main() -> None:
    latest = json.load(open(LATEST, encoding="utf-8"))
    name = latest["versionName"]
    tag = f"android-v{name}"
    asset_name = f"scorpk-assistant-{name}.apk"
    apk = os.path.join(ROOT, "releases", asset_name)
    if not os.path.exists(apk):
        sys.exit(f"No existe {apk}. Ejecuta antes scripts/release_android.py")

    token = credential()
    status, repo = api(token, "GET", f"https://api.github.com/repos/{REPO}")
    if status != 200 or not repo.get("permissions", {}).get("push"):
        sys.exit(f"La credencial no puede escribir en {REPO} (HTTP {status}).")

    status, release = api(token, "GET", f"https://api.github.com/repos/{REPO}/releases/tags/{tag}")
    if status == 404:
        notes = "\n".join(f"- {note}" for note in latest.get("notes", []))
        status, release = api(token, "POST", f"https://api.github.com/repos/{REPO}/releases", {
            "tag_name": tag,
            "target_commitish": "main",
            "name": f"Scorpk Asistente {name}",
            "body": f"{notes}\n\nSHA-256: {latest['sha256']}",
            "draft": False,
            "prerelease": False,
        })
        if status != 201:
            sys.exit(f"No se pudo crear el release (HTTP {status}): {release.get('error')}")
        print("release creado :", release["html_url"])
    else:
        print("el release ya existía:", release.get("html_url"))

    if asset_name in [asset["name"] for asset in release.get("assets", [])]:
        print("el APK ya estaba adjunto")
        return
    upload = release["upload_url"].split("{")[0] + f"?name={asset_name}"
    status, asset = api(token, "POST", upload, data=open(apk, "rb").read(),
                        content_type="application/vnd.android.package-archive")
    if status != 201:
        sys.exit(f"No se pudo subir el APK (HTTP {status}): {asset.get('error')}")
    print("APK subido     :", asset["name"], asset["size"], "bytes")


if __name__ == "__main__":
    main()
