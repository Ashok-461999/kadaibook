# KadaiBook mobile app

Flutter capture client for [KadaiBook](../README.md), the AI notebook digitizer
for small shops. The app is intended to photograph handwritten credit notebook
pages and hand them to the Core API for extraction and review.

## Status

This is the initial Flutter scaffold. The capture flow and Core API integration
are still in progress; the review workspace currently lives in
[`review-ui`](../review-ui).

## Run

```powershell
flutter pub get
flutter run
```

## Check

```powershell
flutter analyze
flutter test
```
