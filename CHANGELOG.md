# Changelog

## [1.0.3](https://github.com/mpaulse/mobitra/releases/tag/1.0.3) - 2021-01-01
### Fixes:
- Data usage between 23:00 and 23:59 were allocated to the next day.

## [1.0.2](https://github.com/mpaulse/mobitra/releases/tag/1.0.2) - 2020-12-11
### Fixes:
- The tracked data usage amounts could drift from the actual totals reported by
  Telkom, causing the daily and monthly graphs to be plotted incorrectly.

## [1.0.1](https://github.com/mpaulse/mobitra/releases/tag/1.0.1) - 2020-07-08
### Fixes:
- The "All" products option on the Active Products tab included exhausted products'
  data when those products' expiration dates have not yet elapsed.

## [1.0](https://github.com/mpaulse/mobitra/releases/tag/1.0) - 2020-04-07
### Improvements:
- Smaller application runtime image.
### Fixes:
- The application window was not visible when the monitor screen configuration changed
  and the window position was outside the bounds of the new screen configuration.
- "Unknown product" is sometimes shown in the status bar at start-up.

## [0.9.3](https://github.com/mpaulse/mobitra/releases/tag/0.9.3) - 2020-03-11
### Fixes:
- A product with a zero available amount was not considered expired, unless the expiry date elapsed.

## [0.9.2](https://github.com/mpaulse/mobitra/releases/tag/0.9.2) - 2020-02-14
### Improvements:
- Display the available amount for the current product in the system tray tooltip.

## [0.9.1](https://github.com/mpaulse/mobitra/releases/tag/0.9.1) - 2020-02-09
### Fixes:
- Charts did not load on the Active Products screen for a newly activated product for which no usage data were captured yet.

## [0.9](https://github.com/mpaulse/mobitra/releases/tag/0.9) - 2020-02-08
### Fixes:
- Product expiry dates were exclusive rather than inclusive.
- Telkom communication errors were reported when attempting to expunge expired products from the list of active products.
- The available amount reported on the status bar and the daily cumulative chart was not updated correctly in realtime. 
- The incorrect available amount was shown for a data point on the daily cumulative chart popup.
- The "Today" label on the daily cumulative chart's x-axis could overlap the "Activation" and "Expiry" labels. 
- Realtime data usage updates affected the daily cumulative charts of products not associated with the current connection.

## [0.8.1](https://github.com/mpaulse/mobitra/releases/tag/0.8.1) - 2020-02-04
### Improvements:
- Support HTTP redirection status codes.
### Fixes:
- The Session ID HTTP cookie was not successfully retrieved from B618 routers.

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
