# Mobile HUD QA v1.0.28

An Android touch overlay for a preexisting TF2 2008 game.
- Same large crouch button: tap to duck; tap again to stand. Color indicates state.
- More > Voces: Z, X and C original separate voice menus, 8 each.
- More > Bots: add one, remove one, quota 0/4/8/12/16, kick all.
  Bot management only has effect if the current player hosts the game.
- Custom difficulty is deliberately left unset. User will provide settings.

## Regenerate on PC
1. Run draw_voice_buttons.ps1
2. Run build_touch_voice_v1.py (Python 3)
3. Run draw_bot_buttons.ps1
4. Run build_bot_panel_v1.py
5. Run verify_mobile_controls_v2.py

The final ZIP contains 108 controlled files. The Android app verifies
each file, backs up prior stock configuration, and preserves user customizations.
QA static test passed; combined controls are not yet verified in live gameplay
because Samsung A34 USB disconnected during the session. APK v1.0.28
was compiled with a QA-only signing key, not the normal production key.
