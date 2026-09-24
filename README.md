# Performance SDK

**Your app feels slow. Find out where the time goes.**

A screen opens, but its data is still loading. Scrolling feels fine on your
phone, yet users report stutters. Every team measures things a little differently.

Performance SDK gives Android teams a shared way to measure screen loading,
UI smoothness and their own expensive operations. Start with one screen, attach
its owner, and send the events to the analytics you already use.

## What You Get

| Question | What the SDK gives you |
| --- | --- |
| Where does loading time go? | Separate data-ready and next-frame timings for screens and widgets. |
| Which screens stutter? | Jank and frozen-frame counts, hitch rate and session duration. |
| Who should investigate? | Screen and team identifiers attached to measurements. |
| Is our own code expensive? | Named measurements around requests, mapping or any custom operation. |
| Does device context matter? | Device tiers, refresh rate and process CPU/memory data; plug in your own network-quality classification. |

**One measurement API across teams. Your data stays in your pipeline.**
Use Compose or XML Views, define your own screen and team names, and route
events through a callback. No required dashboard service or analytics vendor.

## Try It On One Screen

The included app lets you navigate between screens, load a recommendations
widget and inspect the events as they arrive.

```shell
git clone https://github.com/timkabot/performance-sdk.git
cd performance-sdk
./gradlew :sample-app:installDebug
```

Requires repository access, JDK 21, Android SDK 36 and a connected Android 8.0+
device or emulator. Open **Performance Sample** after installation.

To add a measurement to your own app, initialize the SDK with an event callback,
then wrap the operation you care about:

```kotlin
PerformanceSdk.init(
    context = application,
    eventCallback = { event -> analytics.enqueue(event) },
)

// In a coroutine: measure the request and keep its result.
val products = PerformanceMeasure().measureSuspend(
    space = "Catalog".asPerformanceSpace(),
    owner = "Discovery".asPerformanceOwner(),
    stage = PerformanceStage.NetworkRequestsTotalTime("loadProducts"),
) {
    repository.loadProducts()
}
```

`analytics` and `repository` are your application's components. The SDK supplies
the timing and event metadata; your existing pipeline stores and visualizes them.
See [Compose tracing](#compose-tracing) or [XML tracing](#xml-view-tracing) next.

## Dependency

This private standalone copy is not published to a public Maven repository.
The sample app uses `implementation(project(":performance-sdk"))`.
For another local project, publish to Maven Local and add `mavenLocal()` to
that project's repositories before using the coordinates below.

```kotlin
implementation("io.github.timkabot.performance:performance-sdk:<version>")
```

## Initialization

```kotlin
PerformanceSdk.init(
    context = application,
    configProvider = {
        // Must be an in-memory read. Do not do DI, disk, network, or locks here.
        PerformanceConfig(
            enabled = remoteConfig.performanceEnabled,
            sampleRate = remoteConfig.performanceSampleRate,
        )
    },
    eventCallback = { event ->
        // Consumer-owned delivery policy:
        // Clickstream, Firebase, Datadog, custom backend, local logs, batch queue, etc.
        println(event)
    },
    eventBufferCapacity = 256,
)
```

`eventCallback` and SDK logging are dispatched on the SDK callback dispatcher,
not on the thread that emitted the metric. Keep the callback non-blocking anyway:
send to a queue, analytics SDK, or background pipeline owned by the app.
Events are delivered through a bounded buffer. If a producer emits faster than
the callback can consume, the SDK drops the oldest buffered events instead of
blocking UI or growing memory without bounds.

## Spaces And Owners

Consumers can use their own enums by implementing `PerformanceSpace` and
`PerformanceOwner`.

```kotlin
enum class Team(override val performanceName: String) : PerformanceOwner {
    Checkout("Checkout"),
    Discovery("Discovery"),
}

enum class Screen(override val performanceName: String) : PerformanceSpace {
    Main("Main"),
    Cart("Cart"),
}
```

For quick integration, strings can be wrapped:

```kotlin
"Main".asPerformanceSpace()
"Discovery".asPerformanceOwner()
```

## Measuring Code

```kotlin
val measure = PerformanceMeasure()

val cart = measure.measureSuspend(
    space = Screen.Cart,
    owner = Team.Checkout,
    stage = PerformanceStage.NetworkRequestsTotalTime("loadCart"),
) {
    cartRepository.loadCart()
}
```

Custom traces:

```kotlin
measure.measureTrace(
    PerformanceTrace(
        key = "cart_mapper",
        attributes = mapOf("source" to "checkout"),
    ),
) {
    mapper.mapCart()
}
```

## Compose Tracing

```kotlin
val dataReadyCondition = remember(viewState) {
    DataReadyCondition.FlowDataReady(viewState.map { it.isLoaded })
}

UiScreenTracer(
    space = Screen.Main,
    owner = Team.Discovery,
    viewName = "MainScreen",
    dataReadyCondition = dataReadyCondition,
) {
    MainScreenContent()
}
```

Use `UiWidgetTracer` for a small number of independently loading heavy widgets.
Do not wrap every list item with widget tracing.

UI tracers do not use DI in composition. Sampling is remembered per tracer
instance, and environment/runtime snapshots are collected outside the Compose
hot path. Heavy runtime fields such as battery and process memory are attached
only to screen session events.

## XML (View) Tracing

`UiScreenTracer`/`UiWidgetTracer` are Compose-only. Fragments and Activities
backed by XML layouts use the class-based equivalents `ViewScreenTracer`/
`ViewWidgetTracer`, which emit the same event shape (`UiFirstFrame`,
`UiDataReady`, `UiSession`, same stage ids and attributes) from plain
lifecycle callbacks instead of composition effects:

```kotlin
class CartFragment : Fragment() {
    private val tracer = ViewScreenTracer(
        space = Screen.Cart,
        owner = Team.Checkout,
        viewName = "CartFragment",
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tracer.start(requireActivity().window)
        viewLifecycleOwner.lifecycleScope.launch {
            tracer.awaitDataReady(DataReadyCondition.FlowDataReady(viewModel.dataReady))
        }
    }

    override fun onDestroyView() {
        tracer.stop()
        super.onDestroyView()
    }
}
```

For a `DialogFragment`/`BottomSheetDialogFragment`, pass `dialog!!.window`
instead of the host Activity's window — bottom sheets render into their own
`Window`, which the Activity's jank tracker would not observe.

Use `ViewWidgetTracer` for an independently loading section of an XML screen
(data-ready timing only, no jank/session tracking) — the View equivalent of
`UiWidgetTracer`.

Sampling for a `ViewScreenTracer`/`ViewWidgetTracer` instance is decided once,
at first use, and held for its whole session — either all of its
events are emitted or none of them are, matching the Compose tracers'
per-composition sampling. This differs from `PerformanceMeasure`/`saveTime`
calls elsewhere in your code, which sample independently on every call.

### Rolling tracing out across an app: `TracedScreenLifecycleCallbacks`

Wiring `start`/`stop` into every screen by hand, or into a shared
`BaseFragment`, is fragile in a large app: a base-class override only fires
for subclasses that call `super`, and it only ever covers one `BaseFragment`
hierarchy — apps with more than one (e.g. a separate one for bottom sheets)
need it added to each. `TracedScreenLifecycleCallbacks` avoids both problems
by hooking `FragmentManager` itself, which dispatches regardless of `super`
calls or base class:

```kotlin
supportFragmentManager.registerFragmentLifecycleCallbacks(
    TracedScreenLifecycleCallbacks(),
    /* recursive = */ true,
)
```

Screens opt in by implementing `TracedScreen`:

```kotlin
class CartFragment : Fragment(), TracedScreen {
    override val performanceSpace = Screen.Cart
    override val performanceOwner = Team.Checkout
    override val performanceDataReady: DataReadyCondition
        get() = DataReadyCondition.FlowDataReady(viewModel.dataReady)
}
```

Everything else — constructing the tracer, calling `start`/`awaitDataReady`
on the right window, calling `stop` when the view is destroyed — is handled
by the callback. `DialogFragment`/`BottomSheetDialogFragment` are detected
automatically and traced against their own `Window`, not the host Activity's.

Fragments kept alive via `hide()`/`show()` (bottom navigation tabs added once
and then hidden/shown instead of replaced) do not get `onFragmentViewDestroyed`
on tab switch — a session started this way would never end or restart. Call
`notifyHidden(fragment)` / `notifyShown(fragment)` explicitly at the exact
point your navigation hides/shows a tab:

```kotlin
transaction.hide(currentFragment)
tracedScreenCallbacks.notifyHidden(currentFragment)
transaction.show(newFragment)
tracedScreenCallbacks.notifyShown(newFragment)
```

A fragment is always traced in `Screen` mode through this path — it is a
screen, not a widget. For an independently loading section *within* a screen,
call `ViewWidgetTracer` directly at that section's call site instead of
implementing `TracedScreen` on it.

## Startup

```kotlin
val startupTracker = StartupPerformanceTracker()
startupTracker.install(application)
```

By default it reports the first resumed activity. Apps with Fragment/Compose
navigation can disable activity auto-reporting and call `markInteractive` from
their own first-screen callback:

```kotlin
val startupTracker = StartupPerformanceTracker(autoTrackActivityResumed = false)
startupTracker.install(application)

// Fragment/Compose first interactive point:
startupTracker.markInteractive(screenName = "MainFragment")
```

## Runnable Sample App

The repo contains `:sample-app`, a regular Android application that depends on
`project(":performance-sdk")`. It can be launched from Android Studio or from
the command line:

```shell
export ANDROID_HOME=$HOME/Library/Android/sdk # or create local.properties with sdk.dir
./gradlew :sample-app:installDebug
$ANDROID_HOME/platform-tools/adb shell am start -n io.github.timkabot.performance.sample/.MainActivity
```

The sample app shows:

- a `NavHost` with Catalog, Product, and Checkout routes;
- `UiScreenTracer` on every screen route;
- `UiWidgetTracer` for an independently loaded recommendations widget;
- `InitViewModelTime`, `NetworkRequestsTotalTime`, `MapToDomainTime`, and a
  custom `PerformanceTrace("catalog_prepare_ui")`;
- `DataReadyCondition.FlowDataReady(feature.dataReady)` passed from screen
  state to the tracer;
- callback and SDK logs rendered on screen.

All Navigation, Activity Compose, and Material dependencies are scoped to
`:sample-app`; they are not published as SDK dependencies.

## Testing With Logs

The repo also contains unit tests for callback delivery, buffering, and event
shape:

```shell
./gradlew :performance-sdk:testDebugUnitTest --tests io.github.timkabot.performance.sample.PerformanceSdkSampleTest
```

The tests use consumer-owned enums, a fake callback, and logs both SDK events
and outgoing events:

```text
SDK kind=StageDuration stage=NetworkRequestsTotalTime duration=42ms ...
SEND StageDuration: stage=NetworkRequestsTotalTime, duration=42, attrs=...
```

## Publishing

Publish to Maven Local:

```shell
./gradlew :performance-sdk:publishToMavenLocal -PperformanceSdkVersion=<version>
```

The SDK version can be passed as `-PperformanceSdkVersion=<version>` or set as
`PERFORMANCE_SDK_VERSION` in `gradle.properties`.

The build uses a local version catalog and dependencies from Google Maven and
Maven Central. It does not require corporate credentials. Use JDK 21 and
Android SDK 36.

## Measurement Boundaries

The current screen tracers stop first-frame and data-render measurements at a
Choreographer callback, not at frame commit or display presentation. These
durations must not be interpreted as exact time until content is visible.
Keep the data-ready Flow stable across recompositions.

## Distribution Status

Private development copy. No open-source license has been selected. Public
distribution and licensing must be agreed before changing repository visibility.
