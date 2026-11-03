# Persistent Streaming and Automatic Recovery

## Intended behavior

Artemis keeps an active Moonlight connection while the Game Activity is merely
stopped. If the connection fails unexpectedly, it retries with capped exponential
backoff and jitter, without requiring the user to press Connect. A foreground
notification provides a user-visible disconnect control.

## Session lifecycle

- **Start:** a user starts a stream from the Artemis UI. A user-visible
  foreground service begins and the decoder attaches to the view Surface.
- **Background:** Activity pause releases input capture, then Activity stop
  parks decoder output on a headless Surface. No decoded frames are displayed
  in the background. The existing Moonlight transport remains active.
- **Foreground:** the decoder switches back to the new view Surface using
  MediaCodec.setOutputSurface(), where supported.
- **Unexpected disconnect:** the native bridge is stopped once. Artemis
  attempts to start the same connection again using the existing host/app
  selection. Backoff starts near 1 second and is capped near 30 seconds
  (with +/-20% jitter). A newly available network accelerates the next try.
- **Stop/quit:** an explicit disconnect cancels all further retries and
  releases the session resources.

The retry loop never invokes quitAndLaunch() against a different host session.
Authentication, pairing, and session-ownership errors require manual action.

## Limits and fallback

The headless decoder path requires Android API 23+ and codec support for
setOutputSurface(). Unsupported or faulty decoders fall back to connection
recovery instead of pretending the stream was preserved. Recovery can be
delayed while no valid output Surface is available.

The service reduces the chance of process eviction; it is not a replacement
for a fully independent transport service. Android may kill the process,
restrict background service execution, or force-stop the app. In those cases,
recovery resumes when Android permits the app to run again. These cases
require validation on target hardware. A running foreground service does
not guarantee continuous background execution on every OEM ROM.

## Verification matrix

1. Start a desktop session against Apollo and confirm the foreground service
   notification.
2. Press Home and return; check the same stream survives without reconnection.
3. Destroy and recreate the Surface; verify display recovers without a black
   screen where output switching is supported.
4. Toggle Wi-Fi and restore connectivity; verify automatic retry and full
   video/audio/input recovery.
5. Restart Apollo; verify automatic retry with no manual Connect.
6. Start a different app on Apollo during recovery; Artemis must not terminate
   it automatically.
7. Tap Disconnect (or quit inside the app); verify no retries are started.
8. Repeat with PiP, split screen, screen off, Android 13, and an OEM-restricted
   Android build.

The CI build checks compile/test behavior, not real-device decoder or Android
background-lifecycle behavior. Device acceptance is required before release.

## Additional connection safety (PR hardening)

- Each connection start receives a monotonically increasing generation. Callbacks
  from older or cancelled attempts are discarded before reaching the Activity.
- HTTP preflight and native connection setup from consecutive starts are
  serialized to prevent overlapping changes to the connection context.
- Android Recent Apps can return to the streaming Activity; the foreground
  notification also opens that activity when the session is still alive.
- Input capture and controller sensors are released or disabled when Game
  loses foreground focus, then resumed when the user returns.
- The final native stop/parked Surface cleanup also runs if the Activity is
  destroyed during retry backoff.

Known limitation: The foreground service currently protects an Activity-owned
session. It does not own and restore a transport across Android process death.
The hardware/OS acceptance tests above remain mandatory.

## Global bridge ownership and termination policy

A cancelled or failed HTTP preflight does not own Moonlight-core and cannot
interrupt another native connection. Bridge permits are released only by the
owning instance, and permit acquisition polls cancellation rather than waiting
forever after an obsolete attempt. Explicit user disconnect and graceful host
termination are not retried. Unexpected transport loss is retried indefinitely
while the Activity/session remains alive and Android permits execution.
