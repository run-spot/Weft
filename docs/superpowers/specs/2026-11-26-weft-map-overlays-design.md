# Weft Provider-Neutral Map Overlays Design

## Goal

Extend the existing shared map composition boundary with provider-neutral polyline, polygon, and circle overlays. The same `commonMain` `ComposeNode`/`AbstractApplier` tree that currently materializes markers must publish immutable overlay snapshots consumed by the Kakao Android and iOS adapters.

## Decision

Use a minimal provider-neutral contract rather than copying RunTheSpot's product-specific API. The first overlay contract contains identity, geographic geometry, basic appearance, draw order, and visibility. Provider-specific SDK options remain in platform adapters or future extensions.

## Scope

### In scope

- Add immutable `MapPolyline`, `MapPolygon`, and `MapCircle` models to `:weft-core` `commonMain`.
- Validate overlay identity and geometry at model construction time.
- Add `MapScope.Polyline`, `MapScope.Polygon`, and `MapScope.Circle` composables backed by `ComposeNode`.
- Add overlay node types to the existing shared `MapNode`/`MapApplier` tree.
- Materialize ordered, immutable overlay lists in `MapContent` and reject duplicate IDs within each overlay kind.
- Preserve overlay identity across updates, removals, moves, and visibility changes.
- Render overlays through Kakao shape layers on Android and iOS without leaking SDK types into `commonMain`.
- Reconcile overlay snapshots transactionally and preserve the native camera on content-only updates.
- Add common composition tests and platform reconciler/coordinator tests.

### Out of scope

- Holes, geodesic/pattern styling, gradients, caps, joins, tap callbacks, hit testing, animation, editing, and user-location overlays.
- Provider-specific layer IDs, style handles, SDK objects, and platform callbacks in the shared contract.
- A generic provider abstraction beyond the existing `MapPlugin` contract.

## Public common API

All geographic coordinates use the existing validated `LatLng` model. Overlay models are immutable value types with value equality and copy incoming point lists defensively. They expose `copy(...)` helpers while keeping the defensive point snapshot private to construction.

```kotlin
public class MapPolyline(
    public val id: String,
    points: List<LatLng>,
    public val color: Color = Color(0xFF0B57D0),
    public val widthDp: Float = 6f,
    public val zIndex: Float = 0f,
    public val visible: Boolean = true,
)

public class MapPolygon(
    public val id: String,
    points: List<LatLng>,
    public val fillColor: Color = Color(0x1F3366FF),
    public val strokeColor: Color = Color(0x803366FF),
    public val strokeWidthDp: Float = 2f,
    public val zIndex: Float = 0f,
    public val visible: Boolean = true,
)

public class MapCircle(
    public val id: String,
    public val center: LatLng,
    public val radiusMeters: Double,
    public val fillColor: Color = Color(0x1F3366FF),
    public val strokeColor: Color = Color(0x803366FF),
    public val strokeWidthDp: Float = 2f,
    public val zIndex: Float = 0f,
    public val visible: Boolean = true,
)
```

Validation rules:

- Every ID is non-blank.
- A polyline has at least two points.
- A polygon has at least three points.
- Circle radius is finite and strictly positive.
- Widths are finite and non-negative; circle and polygon stroke widths may be zero.
- Point order is preserved and lists are copied so callers cannot mutate a published snapshot.

IDs are unique within each content collection (`markers`, `polylines`, `polygons`, and `circles`). IDs may be reused across different kinds because native adapters prefix their provider-owned shape IDs by kind.

`MapContent` exposes defensive copies of `markers`, `polylines`, `polygons`, and `circles`. `visible = false` stays in the snapshot so the node retains identity; providers must not draw the hidden native shape.

## Composition and node tree

The existing `rememberMapContent` child composition continues to own one `MapRootNode` and one `MapApplier`. Each overlay composable uses `key(overlay.id)` and creates a typed node:

- `MapPolylineNode` stores and updates one `MapPolyline`.
- `MapPolygonNode` stores and updates one `MapPolygon`.
- `MapCircleNode` stores and updates one `MapCircle`.

Each update replaces the immutable model and invalidates the content state exactly once when the model changes. `MapContentBuilder` traverses the tree in child order, fills one list per kind, and rejects duplicate IDs before publishing a new snapshot. `MapNode` insertion, removal, movement, detachment, and invalidation semantics remain shared with markers.

The public DSL has both model overloads and convenience overloads so callers can choose a prebuilt immutable model or pass the fields directly:

```kotlin
WeftMap(state) {
    Polyline(id = "route", points = routePoints)
    Polygon(id = "service-area", points = areaPoints)
    Circle(id = "pickup-radius", center = center, radiusMeters = 500.0)
}
```

## Provider rendering

`KakaoMapAndroidMapViewHost` owns one provider shape layer and `KakaoMapIosMapViewHost` owns one provider shape layer. Platform code converts `LatLng` to SDK coordinates and `Color`/dp values to SDK style values. No SDK class crosses the `:weft-core` boundary.

Each platform reconciler compares the immutable snapshot by type and ID:

- New ID: add one native shape.
- Existing ID with changed model: update geometry, style, z-order, and visibility in place when supported.
- Removed ID: remove the native shape.
- Hidden model: retain the reconciler entry and hide/remove the native shape according to SDK capability.
- Native mutation failure: restore the last committed snapshot and surface one `KakaoMapException` through the existing coordinator error path.

Camera-only updates and content-only updates are separate. A content-only update passes a null camera to the host and must not reset a user pan. A failed camera or shape pass marks native state as uncertain so the next render retries the complete committed snapshot.

The first adapters use the SDK's basic shape primitives. Circles are projected to a provider polygon approximation on platforms whose shape API has no native circle primitive; the projection constant and segment count remain platform implementation details.

## Testing strategy

- `:weft-core:commonTest`: model validation/defensive copies; composition add/update/remove/move/visibility; ordered snapshots; duplicate IDs per kind; atomic publication after a failed build.
- Android host tests: fake shape layer reconciliation for add/update/remove/restore, visibility, style/geometry changes, rollback after partial failure, and camera-preserving content updates.
- iOS tests: mirror the reconciler and coordinator guarantees with a fake host/layer; verify the shared snapshot reaches the host without camera resets.
- Provider compilation: run the relevant Android host tests and iOS simulator test/compile tasks when the local toolchain is available. The repository's existing Android verification requires a configured Android SDK.

## Acceptance criteria

1. A common caller can declare all three overlay kinds without importing Kakao SDK types.
2. Overlay updates flow through the same shared Node + Applier mechanism as markers.
3. `MapContent` publishes ordered, immutable, type-separated overlay snapshots and rejects invalid/duplicate content.
4. Kakao Android and iOS render and reconcile all three kinds with stable IDs and transactional rollback.
5. Existing marker behavior and camera-preservation tests remain green.
