# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick Reference

### Build & Development
- **Build project**: `./gradlew build`
- **Run tests (all)**: `./gradlew test`
- **Run single test**: `./gradlew test --tests io.github.tmarsteel.flyingnarrator.HermiteSplineTest`
- **Clean build**: `./gradlew clean`
- **Run main application**: `./gradlew run` (if run task configured) or run `CodriverApp.main()` from IDE
- **Code style**: Uses Kotlin official style (`kotlin.code.style=official`). Use the Kotlin `object.property = value` syntax for the Java `object.setProperty(value)` setters.

## Tech Stack

- **Language**: Kotlin (JVM 21)
- **Build System**: Gradle (Kotlin DSL)
- **Testing**: Kotest (FreeSpec style)
- **Serialization**: kotlinx-serialization (JSON) + Jackson (XML)
- **Audio**: Opus codec (via libopus native library), OggOpus format, javax.sound.sampled
- **TTS**: Google Cloud Text-to-Speech integration with SSML support
- **UI**: Swing (with FlatLaf look and feel) + `signal-jvm` v3.0.1 (`io.github.fenrur`) for reactive state
- **Game Integration**: Dirt Rally 2.0 telemetry receiver (UDP), race data parsing from XML
- **Other Notable**: OkHttp (HTTP), Rhino (JavaScript), JNA (native interop), Protocol Buffers

## Project Structure

The application is a **co-driver audio narration system** for rally racing games, specifically Dirt Rally 2.0 and EA Sports WRC. It generates and plays back pace notes (driving instructions) synchronized with game telemetry.

### Main Packages

```
io.github.tmarsteel.flyingnarrator/
├── audio/              # Audio playback and format handling
│   └── opus/          # Opus codec integration, OggOpus encoding/decoding
├── codriver/          # Main application (CodriverApp), game observation interface
├── dirtrally2/        # Dirt Rally 2.0 specific implementation
│   └── gamemodels/    # XML/protobuf models for DR2 data structures
├── easportswrc/       # EA Sports WRC support
├── pacenote/          # Pace note atoms and cuing logic
│   └── inferred/      # Inferred pacenote generation
├── tts/               # Text-to-speech synthesis interface
│   ├── gcloud/        # Google Cloud TTS implementation
│   └── ssml/          # SSML document building
├── route/             # Route representation and reading abstraction
├── geometry/          # 3D geometry (vectors, splines, hermite curves)
├── feature/           # Feature detection from routes (corners, straights)
├── nefs/              # NEFS file format support (Ego engine archives)
├── unit/              # Custom unit types (Distance, etc.)
├── io/                # JSON/XML serialization utilities
├── http/              # HTTP utilities
├── editor/            # Route editing UI components
└── ui/reactive/       # Lifecycle-aware signal subscription helpers (LifecycleSignalSubscription, subscribeOn)
```

## Architecture Overview

### High-Level Flow

1. **Game Observation** (`codriver/GameObserver`):
   - `DirtRally2TelemetryReceiver` listens for UDP telemetry packets from the game
   - Parses telemetry to track race progress (0.0 to 1.0 fraction)
   - Signals `GameObserver.Listener` with stage start, progress updates, and pause/resume events

2. **Route Reading**:
   - `DirtRally2RouteReader` parses game track data (XML files: `track_spline.xml`, `progress_track.xml`)
   - Constructs a `Route` from spline control points and progress gates
   - Hermite spline interpolation ensures smooth road representation

3. **Pace Notes Generation**:
   - `PacenoteAtom` is the abstraction for any describable route feature (corner, straight, jump, etc.)
   - `AudioPacenotes` holds pre-recorded/synthesized audio clips with markers indicating where each note starts in the audio stream
   - `CuedAudioPacenotes` matches audio clips to driver position during race and queues them for playback

4. **Audio Playback** (`audio/`):
   - `ClipQueue` manages playback of audio clips
   - OggOpus codec support via native libopus library bindings
   - Clips are encoded as OggOpus for efficiency

5. **UI** (`CodriverApp`, `editor/`):
   - Swing-based GUI with state machine (idle → race in progress → finished)
   - Displays current stage name and elapsed race time
   - Updates in real-time as telemetry arrives
   - Editor components use `signal-jvm` for reactive state: `signalOf()` for read-only signals, `mutableSignalOf()` for mutable state, `combine()` for derived state. Subscriptions bind to Swing component lifecycle via `LifecycleSignalSubscription` / `.subscribeOn()` extension (in `ui/reactive/`) to avoid leaks on mount/unmount.

### Key Design Patterns

- **Observer Pattern**: Game events flow through `GameObserver.Listener` interface; editor UI state uses `signal-jvm` signals instead of manual listeners
- **Route Abstraction**: `RouteReader` interface allows support for different game formats (DR2, WRC, etc.)
- **Serialization Abstraction**: Pluggable TTS and audio format support
- **3D Geometry**: Custom `Vector3` and `HermiteSpline` for road reconstruction from control points

## Game Data Integration

### Dirt Rally 2.0
- Reads track geometry from `track_spline.xml` (Hermite spline control points)
- Reads race progress data from `progress_track.xml` (gates defining stage splits)
- Receives real-time telemetry via UDP socket on localhost (IP/port cached in `.cache/dr2.telemetry-race-progress`)
- Game coordinate system conversion (`DirtRally2CoordinateSystem`)

### NEFS Archives
- Can extract and parse race data from Ego engine NEFS archives (compressed game asset files)
- Requires `nefsedit-cli` command-line tool (built separately, included as GitHub Actions artifact)

## Testing

- Tests are in `src/test/kotlin/`
- Use Kotest's FreeSpec style for test organization
- Example: `HermiteSplineTest` validates geometric interpolation
- Test resources in `src/test/resources/` (includes reference game screenshots)

## Building with Dependencies

The build process has special handling for native dependencies:

1. **Opus Library**: Built separately in GitHub Actions (cross-compiled for Windows x86_64)
   - Artifacts placed in `build/native-libs/opus/win-x64/`
   - Can be manually built with `./gradlew` if needed, but GA builds it in `.github/workflows/build-opus.yml`

2. **NEFS CLI Tool**: Built separately for NEFS file format parsing
   - Artifacts in `build/native-libs/nefsedit-cli/win-x64/`

3. **Protobuf**: Auto-generated from `.proto` files in `nefsedit-cli/nefsedit-cli/protobuf/`

The main `./gradlew build` assumes these artifacts are already in place (GA downloads them).

## Important Files

- **Entry point**: `CodriverApp.main()` - Swing application with telemetry listening
- **Analysis entry point**: `Main.kt` - Command-line tool for route feature analysis (generates CSV)
- **Route abstraction**: `route/Route.kt`, `route/RouteReader.kt`
- **Pacenote interface**: `pacenote/PacenoteAtom.kt`, `pacenote/AudioPacenotes.kt`
- **Game observer**: `codriver/GameObserver.kt`, `dirtrally2/DirtRally2TelemetryGameObserver.kt`

## Notes for Contributors

- All 115+ source files follow standard Kotlin conventions
- Heavy use of typed collections and sealed classes for domain models
- Distances and measurements use custom `unit/` types rather than primitives
- Serialization uses kotlinx-serialization for JSON and Jackson for XML
- No linting/formatting enforced in the build (relies on Kotlin official style)
