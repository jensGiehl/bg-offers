import argparse
import hashlib
import ipaddress
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
CONFIG = ROOT / "scripts" / "price-comparison.curl"
HOST = "www.brettspiel-angebote.de"
RESPONSE_HEADERS = {
    "server", "date", "content-type", "content-length", "content-encoding",
    "cdn-requestid", "cdn-request-id", "request-id", "x-request-id",
    "cdn-challenge", "errorcode", "cdn-cache", "cdn-cachedat",
}
WRITE_OUT = (
    "http_code=%{http_code}\nhttp_version=%{http_version}\n"
    "local_ip=%{local_ip}\nremote_ip=%{remote_ip}\n"
    "remote_port=%{remote_port}\ntime_total=%{time_total}\n"
    "ssl_verify_result=%{ssl_verify_result}\n"
)


def run_command(arguments, timeout=35):
    return subprocess.run(
        arguments, capture_output=True, timeout=timeout,
        encoding="utf-8", errors="replace", check=False,
    )


def response_headers(raw_headers):
    selected = {}
    for line in raw_headers.splitlines():
        if line.startswith("HTTP/"):
            selected = {}
        name, separator, value = line.partition(":")
        if separator and name.lower() in RESPONSE_HEADERS:
            selected.setdefault(name.lower(), []).append(value.strip())
    return selected


def probe(curl, name, options):
    with tempfile.TemporaryDirectory(prefix="bg-offers-probe-") as directory:
        body_path = Path(directory) / "body"
        headers_path = Path(directory) / "headers"
        arguments = [
            curl, "--disable", "--config", str(CONFIG),
            "--noproxy", "*", "--silent", "--show-error",
            "--connect-timeout", "10", "--max-time", "30",
            "--dump-header", str(headers_path), "--output", str(body_path),
            "--write-out", WRITE_OUT, *options,
        ]
        try:
            result = run_command(arguments)
        except subprocess.TimeoutExpired:
            return {"name": name, "error": "Diagnoseprozess nach 35 Sekunden abgebrochen"}
        body = body_path.read_bytes() if body_path.exists() else b""
        raw_headers = headers_path.read_text(encoding="utf-8", errors="replace") if headers_path.exists() else ""
        metadata = dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)
        return {
            "name": name,
            "curl_exit_code": result.returncode,
            **metadata,
            "response_headers": response_headers(raw_headers),
            "wire_body_bytes": len(body),
            "wire_body_sha256": hashlib.sha256(body).hexdigest(),
            "error": result.stderr.strip(),
        }


def ip_argument(value):
    try:
        return ipaddress.ip_address(value)
    except ValueError as exception:
        raise argparse.ArgumentTypeError("Bitte eine IPv4- oder IPv6-Adresse angeben") from exception


def system_details(curl, jar):
    version = run_command([curl, "--disable", "--version"])
    details = {
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "platform": platform.platform(),
        "architecture": platform.machine(),
        "curl_version": version.stdout.strip(),
        "curl_exit_code": version.returncode,
        "curl_config_sha256": hashlib.sha256(CONFIG.read_text(encoding="utf-8").encode("utf-8")).hexdigest(),
        "direct_connection": True,
    }
    java = shutil.which("java")
    if java:
        result = run_command([java, "-version"])
        java_version = result.stdout + result.stderr
        details["java_version"] = "\n".join(
            line for line in java_version.splitlines()
            if not line.startswith(("Picked up ", "NOTE: Picked up "))
        )
    else:
        details["java_version"] = "Java nicht im PATH gefunden"
    details["java_option_variables_present"] = [
        name for name in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")
        if name in os.environ
    ]
    if jar:
        with jar.open("rb") as source:
            digest = hashlib.sha256()
            for block in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(block)
        details["jar_sha256"] = digest.hexdigest()
    return details


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="Vergleicht direkte curl-Startseitenabrufe ohne Cookies und Wiederholungen.")
    parser.add_argument("--only", choices=("ipv6-http1", "ipv6-http2", "ipv4-http1"))
    parser.add_argument("--tls12", action="store_true", help="Zusätzlicher IPv6-/HTTP/1.1-Test mit ausschließlich TLS 1.2")
    parser.add_argument("--remote-ip", type=ip_argument, help="Feste Ziel-IP, mit unverändertem Hostnamen und TLS-SNI")
    parser.add_argument("--source-ip", type=ip_argument, help="Bereits lokal konfigurierte Quell-IP")
    parser.add_argument("--jar", type=Path, help="Berechnet zusätzlich die SHA-256 des zu vergleichenden JARs")
    parser.add_argument("--output", type=Path, help="Berichtspfad; vorhandene Dateien werden nicht überschrieben")
    args = parser.parse_args()
    curl = shutil.which("curl.exe" if sys.platform == "win32" else "curl")
    if not curl:
        parser.error("curl wurde nicht im PATH gefunden")
    if args.jar and not args.jar.is_file():
        parser.error("JAR-Datei nicht gefunden")
    if args.remote_ip and args.source_ip and args.remote_ip.version != args.source_ip.version:
        parser.error("Quell- und Ziel-IP müssen dieselbe Adressfamilie verwenden")
    if args.only and args.tls12:
        parser.error("--tls12 bitte ohne --only verwenden")
    family = (args.remote_ip or args.source_ip)
    if args.only and family and int(args.only[3]) != family.version:
        parser.error("Der gewählte Test passt nicht zur Adressfamilie der IP")
    report_path = args.output or ROOT / "diagnostics" / (datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + ".json")
    report_path.parent.mkdir(parents=True, exist_ok=True)
    try:
        report_file = report_path.open("x", encoding="utf-8")
    except FileExistsError:
        parser.error("Der Bericht existiert bereits; bitte einen neuen --output-Pfad verwenden")
    with report_file:
        report = system_details(curl, args.jar)
        report["requested_remote_ip"] = str(args.remote_ip) if args.remote_ip else None
        report["requested_source_ip"] = str(args.source_ip) if args.source_ip else None
        common = []
        if args.remote_ip:
            address = f"[{args.remote_ip}]" if args.remote_ip.version == 6 else str(args.remote_ip)
            common.extend(["--resolve", f"{HOST}:443:{address}"])
        if args.source_ip:
            common.extend(["--interface", str(args.source_ip)])
        variants = [
            ("ipv6-http1", 6, ["--ipv6", "--http1.1"]),
            ("ipv6-http2", 6, ["--ipv6", "--http2"]),
            ("ipv4-http1", 4, ["--ipv4", "--http1.1"]),
        ]
        if args.tls12:
            variants.append(("ipv6-http1-tls12", 6, ["--ipv6", "--http1.1", "--tlsv1.2", "--tls-max", "1.2"]))
        features = re.search(r"^Features:\s*(.*)$", report["curl_version"], re.MULTILINE)
        http2_supported = features is not None and "HTTP2" in features.group(1).split()
        results = []
        for name, version, options in variants:
            if args.only and name != args.only:
                continue
            if family and version != family.version:
                continue
            if name == "ipv6-http2" and not http2_supported:
                result = {"name": name, "skipped": "curl unterstützt kein HTTP/2"}
            else:
                print(f"Teste {name} ...", flush=True)
                result = probe(curl, name, options + common)
            results.append(result)
            print(json.dumps(result, ensure_ascii=False, indent=2), flush=True)
        report["results"] = results
        json.dump(report, report_file, ensure_ascii=False, indent=2)
        report_file.write("\n")
    print(f"Bericht: {report_path.resolve()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
