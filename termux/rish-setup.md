# rish (Shizuku shell) setup — Tier 2

Tier 2 lets the brain run ADB-level commands (`input`, `pm`, `settings`, `uiautomator`)
without root, via Shizuku.

1. Install **Shizuku** (F-Droid / GitHub).
2. Start Shizuku via **Wireless debugging** (on-device, no PC):
   - Settings → Developer options → Wireless debugging → on.
   - In Shizuku, tap "Start" (pairing).
3. Give Termux the `rish` helper: in Shizuku → "Use in terminal apps", follow the steps to
   copy `rish` and `rish_shizuku.dex` into Termux `$HOME` (`~`).
4. Test:
   ```sh
   ./rish -c 'id'          # expect uid=2000(shell) → Tier 2 is live
   ./rish -c 'input tap 500 1000'
   ```
5. Set `RISH_CMD=~/rish` in `brain/.env` if it isn't on PATH.

> Shizuku must be re-armed after each reboot (a quick wireless-debugging tap). On Android 13+,
> Shizuku 13.6+ can auto-start on trusted Wi-Fi. The brain degrades gracefully to Tier 0/1
> when Tier 2 isn't live (see `capabilities()`).
