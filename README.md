# MIXER by Sanusi 7.0 — Android prototype

This is an Android native prototype using Android Audio Playback Capture (API 29+).

## Important limitations
- It can capture only playback from source apps that allow playback capture and only matching usages.
- Android's MediaProjection consent dialog is required each session.
- The original source audio continues playing; this prototype plays a processed monitor copy. Use headphones to reduce hearing the unprocessed and processed signals together.
- This is not a guaranteed global system-wide insert effect and does not support every app/device.
- The included first prototype has basic master gain and simple tonal shaping controls, not the complete 7-band studio DSP from the web mixer.

## Build APK using GitHub Actions
1. Upload the entire project folder contents to a new GitHub repository (keep folder structure).
2. Open Actions and run `Build MIXER by Sanusi APK` (or push to main).
3. Download the `MIXER-by-Sanusi-7.0-debug` artifact from the completed workflow.
