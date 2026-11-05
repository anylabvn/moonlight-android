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

Lifecycle callbacks that close or activate a stream are validated again when
the main-thread delivery executes, not only when the native callback first
arrives. This prevents queued callbacks from a cancelled attempt from closing
a newer stream while Android's UI thread is busy.

## Reliability and visibility hardening

### Failure detection

- Native connection startup failure (nonzero return code) now reports
  `stageFailed` to the Java recovery owner; it must not leave `connecting`
  true indefinitely.
- A 45-second connection-attempt watchdog recovers from preflight/native
  startup hangs that do not produce an error callback. The watchdog is
  generation-scoped, cancelled on success/cleanup, and applies to foreground
  and background connection attempts.
- Android's default network-loss callback triggers transport recovery. An
  available network can accelerate a scheduled retry. Merely reporting poor
  network quality does *not* automatically destroy a working connection.
- The video decoder records the last received frame against a monotonic clock.
  Once streaming, a 25-second frame stall triggers recovery even if no native
  error callback arrives. A normal video session also triggers recovery if
  its first frame never arrives; input-only sessions are excluded.
- Recovery still serializes stop/retry against the global Moonlight bridge and
  prevents stale native callbacks from affecting a subsequent attempt.

### User-visible connection state

The Game Activity contains a persistent high-contrast connection banner
independent of optional packet-loss warnings. It displays the lost-connection
state, retries with attempt count and approximate delay, and an explicit
connected-again confirmation. During retry, the foreground notification
describes the same attempt/waiting status while the application is backgrounded.
For permanent host/authorization failures, the normal error dialog remains
and the banner says user action is needed.

The banner and service do not imply the OS permits restart after Force Stop or
process death. The *Activity still owns* Moonlight native transport and decoder;
a foreground service is not yet equivalent to moving the entire session
lifecycle into a process-persistent service.

### Additional physical-device acceptance checks

- Turn off Wi-Fi without closing the Activity; verify a visible loss banner
  and a matching reconnecting notification. Restore Wi-Fi and check that
  video, audio and input resume without tapping Connect.
- Disconnect Apollo or the network while Android is in Recent Apps; verify
  reconnect remains armed and the user sees state on reopening Artemis.
- Force a host/native startup rejection; ensure the `connecting` state does
  not stay stuck indefinitely and a new retry occurs.
- Leave the network up but stop incoming video frames; verify a stalled
  session is flagged after the video inactivity threshold.
- Confirm no false reconnect on short network jitter, that manual Disconnect
  cancels retries, and that the Stable app remains unaffected.
