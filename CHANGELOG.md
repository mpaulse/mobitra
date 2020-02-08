# Changelog

## [0.8.2](https://github.com/mpaulse/mobitra/releases/tag/0.8.2) - 2020-02-08
### Fixes:
- Product expiry dates were exclusive rather than inclusive.

## [0.8.1](https://github.com/mpaulse/mobitra/releases/tag/0.8.1) - 2020-02-04
### Improvements:
- Support HTTP redirection status codes.
### Fixes:
- Fix an issue retrieving the Session ID HTTP cookie from B618 routers.

## [0.8](https://github.com/mpaulse/mobitra/releases/tag/0.8) - 2020-02-03
### Improvements:
- Support E5-series routers (e.g. E5573).
- Detect when the connection changes to a different router/SIM using the device name and WIFI SSID.

## [0.7.1](https://github.com/mpaulse/mobitra/releases/tag/0.7.1) - 2020-01-28
### Improvements:
- Bar charts: Display the total amount first before other amounts on the bar information popup.
### Fixes:
- History charts: Inflated data amounts were reported after products expire.

## [0.7](https://github.com/mpaulse/mobitra/releases/tag/0.7) - 2020-01-27
### Improvements:
- Thread-safe database access.
### Fixes:
- The application would not start if the database lock file remained after an unclean shutdown.
- The night-surfer time range is now 12AM-7AM instead of 12AM-8AM.

## [0.6-alpha](https://github.com/mpaulse/mobitra/releases/tag/0.6-alpha) - 2020-01-25
- First public alpha release.
