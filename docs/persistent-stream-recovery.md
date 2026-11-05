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

### Headless video Surface on Android 8+

On Android API 26+, the parked video target now uses the documented
`SurfaceTexture(false)` constructor, which starts detached from an OpenGL
texture. The previous `SurfaceTexture(0)` path remains as a fallback for
older supported Android versions. Decoder output is discarded while parked,
and the real display Surface is restored when returning to the app. Some
device codecs reject `MediaCodec.setOutputSurface` even for supported
Surface types; these failures must trigger full recovery instead of claiming
the live stream was preserved. This compatibility change is grounded in the
Android platform API contract but still requires device validation on MIUI.

## Screen-off reliability and native ownership hardening

The foreground service also holds a partial CPU wake lock while the user-
requested streaming session exists. Android's foreground-service notification
alone does not prevent deep sleep from delaying keepalives and the retry handler.
The wake lock is released by `StreamSessionService.onDestroy()`. This can
increase power consumption while a stream is running in the background; it is
not a bypass for system Force Stop or OEM process termination.

`NvConnection` now claims the global MoonBridge ownership permit under its
native synchronization lock, **after** the last generation/cancellation check.
Previously, a concurrent stop could see ownership before native startup and
attempt to stop an uninitialized connection. A failed native start cleans
Java bridge references and releases the permit before notifying the Activity.
Native stop cleanup also releases the permit if cleanup itself throws.

Automatic retry keeps the exponential failure count across short-lived
connection handshakes. It resets only after an uninterrupted connection has
survived 60 seconds. This reduces fast reconnection storms on flapping networks
and does not change the cap on retries.

**Device acceptance, still required:** screen-off for several minutes, return
to streaming without touching Connect, toggle Wi-Fi and restore Apollo, check
accurate visible state/notification, verify no more than one live connection,
and confirm the explicit Disconnect action releases its foreground wake lock.
A CPU wake lock cannot guarantee that a vendor ROM will keep the process alive.

## Thread-safe failure reporting

Callbacks from Moonlight's HTTP and native startup threads must not update
Android Views directly. Permanent startup failures now mark the session as
no longer desired and **post all banner, watchdog cancellation and foreground
service UI operations to the main thread**; network diagnostics remain off
the UI thread. The former `stageFailed` path could return `true` after
setting `sessionDesired=false`, making a lower-level retry inconsistent with
the Activity's recovery state; this path has been removed.

Unexpected preflight exceptions are reported through the generation-guarded
failure callback instead of silently abandoning the worker thread. Native
interrupt exceptions do not skip cleanup of the bridge and global permit.

These are source-level reliability corrections verified by CI tests and build,
not device-confirmed reconnection. A physical Android/Apollo test still needs
to establish that connection survives backgrounding and returns to usable
audio/video/input after Wi-Fi and host failures.

## Media-level recovery confirmation

A native `connectionStarted()` callback proves that the transport handshake
completed, **not** that the remote desktop is displaying new video. On a
recovered video stream the session banner and notification now report
"Transport connected — waiting for video" until a fresh decoded video unit
arrives. Only then are they changed to "Connected again" and "streaming".
If no frame arrives, the video inactivity watchdog continues monitoring and
returns to recovery instead of falsely acknowledging success. Input-only
sessions (without video) are confirmed from the transport event directly.

Use an Apollo session that can show changing frames when validating the
media-level success indicator; a synthetic native connection test alone does
not cover full video/audio/input restoration.

## Deterministic reconnect rounds — RustDesk-informed hardening

The key transition is **disconnect the old transport → verify cleanup →
start a fresh transport connection**. This does *not* quit the application
on Apollo; the stream reconnect resumes the same host session when allowed.
Users who explicitly disconnect do not enter retry.

Implementation: `StreamReconnectCycle` assigns every recovery an incrementing
round number and enforces this sequence:

```text
ACTIVE/CONNECTING
   |
   | transient network, timeout, decoder or transport failure
   v
STOPPING       -- conn.stop() blocking on recovery worker thread
   |
   | native stop/cleanup completed; previous MoonBridge permit released
   v
WAITING        -- exponential transport backoff
   |
   | usable decoder Surface available + round still current
   v
CONNECTING     -- conn.start() of the replacement transport
   |
   | native handshake and fresh media (for video sessions)
   v
ACTIVE
```

An old cleanup completion cannot move a newer round forward. The screen
resume path now re-enters via `beginStreamRecovery()` (stop first) rather than
directly scheduling another `conn.start()`. Waiting for a local decoder
Surface is retried at a short fixed interval and does not count as another
host/network failure; synchronous startup errors re-enter the stop path.
On Android resume, the video health check runs promptly rather than waiting
for the next periodic watchdog tick.

If native `conn.stop()` itself throws, the old session may still own
resources. The client surfaces a user-action error and does **not** risk
starting a second native session. A retry does not call the Apollo
`quitApp()` endpoint; it must never kill an unrelated desktop session.

### External implementation evidence

- RustDesk PR [#15143](https://github.com/rustdesk/rustdesk/pull/15143)
  (merged June 1, 2026): an Android reconnect wedge was traced to old Rust
  sessions never being closed when Flutter dispose suspended. The fix
  initiated `sessionClose` *before* awaiting UI cleanup.
- RustDesk's [`ui_session_interface.rs`](https://github.com/rustdesk/rustdesk/blob/master/src/ui_session_interface.rs)
  keeps a `connection_round_state`, closes the previous active session,
  and starts the next io loop with a fresh round ID.
- RustDesk PR [#16662](https://github.com/rustdesk/rustdesk/pull/16662)
  (open at review time, **not a merged behavior guarantee**) observes
  background network restrictions on Android; it proposes a single
  reconnect on foreground resume while preserving deliberate remote
  disconnect choices.
- RustDesk PR [#15290](https://github.com/rustdesk/rustdesk/pull/15290)
  (merged June 17, 2026) models remote restart as a bounded reconnect
  state with explicit user feedback.

These are **design analogies**. Artemis speaks Moonlight/Sunshine/Apollo,
not RustDesk's relay protocol. A successful local Java test of the
transaction does not demonstrate Apollo server recovery or Android OEM
background network access.

### Acceptance checks

1. For every automatic retry, verify `conn.stop()` returns and old native
   connection ownership is released *before* another `conn.start()`.
2. Reproduce background/foreground while disconnect is in progress;
   a stale stop must not create a second connection.
3. Restart Apollo and toggle Wi-Fi. Confirm no repeated `Connecting…` trap,
   remote video/audio/input are usable, and the status reflects actual media.
4. Keep the tablet locked, unlock it, and confirm a stranded session is
   detected on resume; do not disrupt a healthy session.
5. Ensure remote/manual disconnection, pairing/ownership denials,
   and user-initiated quit remain non-retryable.
6. Verify the Test package remains independently installable beside Stable.

### Reusing the Android decoder safely

The original automatic reconnect reused `MediaCodecDecoderRenderer` after
native cleanup, but `setup()` only reset `stopping`, the last-video timestamp,
and the video dimensions. Several values belong to the old video session:
NALU parameter sets (VPS/SPS/PPS), codec input indexes and buffers,
queued output indexes, frame-number continuity, timestamp/pacing state,
per-session statistics, and codec-recovery attempts. The new
`resetStreamDecoderState()` clears these **before each new setup**.

The selected Android rendering Surface is deliberately preserved: the
reconnect gate chooses a usable visible or parked Surface *before* native
`startConnection`, and the renderer must not lose that target during setup.
The old worker threads and MediaCodec instance must already be stopped
and cleaned by the native disconnect path; changing these while an old
renderer thread is still active would be unsafe.

This addresses state leakage that would not occur when manually opening
a fresh Game Activity, but it still requires video/audio/input validation
on the actual device. Decoder-compatibility bugs that cannot survive
`MediaCodec.stop()/release()` may need a separate fallback path.

### HTTP launch/resume must not trap the next reconnect round

`NvConnection.start()` performs its HTTP preflight under a per-connection
`startupLock`. In the prior implementation, `NvHTTP.launchApp()` called
the Apollo/Moonlight `launch` and `resume` endpoints through an OkHttp
client with an **unlimited read timeout**. If the host stopped responding
mid-request (for example, during a PC reboot), the old preflight thread
could hold `startupLock` indefinitely. A 45-second UI watchdog could
invalidate that attempt but could not release the blocked Java monitor.
Later retry workers would remain waiting for it, never reaching native
`startConnection`.

The session-establishment HTTP client now has a 15-second read timeout
and a 25-second whole-call timeout. A blocked request becomes a retriable
`IOException`, the worker exits and releases the startup lock, and the
round coordinator can perform another stop-before-start cycle. Other
host HTTP workflows (including pairing/user approval and quit) retain
their existing timeout contracts.

The bounded timeout is a tradeoff: a host taking unusually long to
launch/resume an app may require another retry, instead of holding the
entire client indefinitely. Device acceptance should include Apollo
restarting while the `resume` request is in flight.
