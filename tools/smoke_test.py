#!/usr/bin/env python3
"""Device smoke test: launch, typed navigation, JS, back, bookmarks and tabs.

Requires an installed debug APK and exactly one adb device/emulator. Uses a local
HTTP fixture through adb reverse; does not depend on public websites. Test tab
URLs/bookmarks are created on the connected test device. No app data is cleared.
"""
import argparse
import http.server
import os
from pathlib import Path
import re
import subprocess
import threading
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB", "adb")
OUTPUT = Path(os.environ.get("VEYRA_TEST_OUTPUT", "app/build/smoke"))
PORT = 8765
SERIAL = None


def adb(*args, binary=False):
    return subprocess.check_output([ADB, *(["-s", SERIAL] if SERIAL else []), *args], text=not binary, timeout=45)


def hierarchy():
    adb("shell", "uiautomator", "dump", "/sdcard/veyra-window.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/veyra-window.xml"))


def find(value, attribute="text", partial=False, timeout=35):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            for node in hierarchy().iter("node"):
                candidate = node.get(attribute, "")
                if (value in candidate if partial else candidate == value):
                    return node
        except (ET.ParseError, subprocess.SubprocessError):
            pass
        time.sleep(0.5)
    raise AssertionError(f"UI element missing: {attribute}={value!r}")


def tap(value, attribute="text", partial=False):
    node = find(value, attribute, partial)
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def menu(value):
    tap("Menu", "content-desc")
    tap(value)


class Fixture(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/two"):
            body = "<h1>Tweede pagina werkt</h1><a href='/'>Terug naar testpagina</a>"
            title = "Veyra tweede pagina"
        else:
            title = "Veyra rooktest"
            body = """<h1>Veyra rooktest</h1><p id='js'>JavaScript ontbreekt</p>
<script>document.getElementById('js').textContent='JavaScript werkt';</script>
<p><a href='/two'>Ga naar tweede pagina</a></p>
<p><a href='/two' target='_blank'>Open in nieuw tabblad</a></p>
<label>Bestand kiezen <input type='file'></label>
<img alt='' src='http://ads.doubleclick.net/veyra-smoke.gif'>"""
        payload = ("<!doctype html><html lang='nl'><meta name='viewport' content='width=device-width'>"
                   f"<title>{title}</title><style>body{{font:20px sans-serif;padding:20px;line-height:1.7}}"
                   f"a{{display:block;padding:12px 0}}</style>{body}</html>").encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *_):
        pass


def main():
    global SERIAL
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--confirm-isolated-test-device',action='store_true')
    args=parser.parse_args()
    if not args.confirm_isolated_test_device:parser.error('Use an isolated test device; this test creates tabs and a bookmark. Set the app language to Dutch first.')
    SERIAL=args.serial
    OUTPUT.mkdir(parents=True, exist_ok=True)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), Fixture)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    adb("reverse", f"tcp:{PORT}", f"tcp:{PORT}")
    try:
        adb("shell", "am", "start", "-n", "nl.veyra.browser/.BrowserActivity")
        tap("Zoeken of webadres", "content-desc")
        # Select all through Ctrl+A so this also works with a restored address.
        adb("shell", "input", "keycombination", "113", "29")
        adb("shell", "input", "text", f"http://127.0.0.1:{PORT}/")
        adb("shell", "input", "keyevent", "66")
        find("JavaScript werkt", partial=True)
        print("PASS typed navigation and JavaScript", flush=True)
        tap("Ga naar tweede pagina", partial=True)
        find("Tweede pagina werkt", partial=True)
        tap("Terug", "content-desc")
        find("JavaScript werkt", partial=True)
        print("PASS link navigation and browser back", flush=True)
        menu("Bewaren")
        menu("Bibliotheek")
        find("Veyra rooktest", partial=True)
        adb("shell", "input", "keyevent", "4")
        print("PASS bookmark creation and listing", flush=True)
        menu("Nieuw tabblad")
        find("Het web.", partial=True)
        summary = find("geblokkeerd in deze sessie", partial=True)
        assert int(summary.attrib["text"].split()[0]) > 0, "Tracking fixture was not blocked"
        print("PASS local tracker request blocked", flush=True)
        (OUTPUT / "home.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
        tap("Tabbladen", "content-desc")
        tap("Veyra rooktest", partial=True)
        find("JavaScript werkt", partial=True)
        print("PASS new tab and tab switching", flush=True)
        tap("Open in nieuw tabblad", partial=True)
        find("Tweede pagina werkt", partial=True)
        print("PASS target=_blank opens a navigable tab", flush=True)
        (OUTPUT / "page.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
        print(f"Screenshots: {OUTPUT}", flush=True)
    finally:
        adb("reverse", "--remove", f"tcp:{PORT}")
        server.shutdown()


if __name__ == "__main__":
    main()
