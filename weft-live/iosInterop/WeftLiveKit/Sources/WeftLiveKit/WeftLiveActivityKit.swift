import Foundation

#if os(iOS) && canImport(ActivityKit)
import ActivityKit
#endif

#if os(iOS) && canImport(ActivityKit) && canImport(WidgetKit) && canImport(SwiftUI)
import SwiftUI
import WidgetKit
#endif

/// The content snapshot shared by the Objective-C façade and ActivityKit.
public struct WeftLiveActivitySnapshot: Codable, Hashable {
    public let title: String
    public let subtitle: String?
    public let status: String
    public let progressCurrent: Int64?
    public let progressTotal: Int64?
    public let startedAtMillis: Int64?
    public let endsAtMillis: Int64?
    public let deepLink: String?

    public init(
        title: String,
        subtitle: String?,
        status: String,
        progressCurrent: Int64?,
        progressTotal: Int64?,
        startedAtMillis: Int64?,
        endsAtMillis: Int64?,
        deepLink: String?,
    ) {
        self.title = title
        self.subtitle = subtitle
        self.status = status
        self.progressCurrent = progressCurrent
        self.progressTotal = progressTotal
        self.startedAtMillis = startedAtMillis
        self.endsAtMillis = endsAtMillis
        self.deepLink = deepLink
    }
}

internal struct WeftLiveActivityRecord {
    let id: String
    let state: String

    init(id: String, state: String) {
        self.id = id
        self.state = state
    }
}

internal protocol WeftLiveActivityDriver: AnyObject {
    var availabilityCode: String { get }
    func activities() -> [WeftLiveActivityRecord]
    func request(id: String, state: WeftLiveActivitySnapshot) async throws
    func update(id: String, state: WeftLiveActivitySnapshot) async throws
    func end(id: String, state: WeftLiveActivitySnapshot, cancelled: Bool) async throws
}

internal enum WeftLiveActivityDriverError: Error {
    case unsupported
    case disabled
    case invalidState
    case platformFailure
    case notFound
}

/// Result returned by every Objective-C-compatible bridge operation.
@objc(WeftLiveActivityBridgeResult)
public final class WeftLiveActivityBridgeResult: NSObject {
    @objc public let accepted: Bool
    @objc public let failureCode: String?
    @objc public let failureMessage: String?

    fileprivate init(accepted: Bool, failureCode: String? = nil, failureMessage: String? = nil) {
        self.accepted = accepted
        self.failureCode = failureCode
        self.failureMessage = failureMessage
    }
}

/// Objective-C-compatible ActivityKit lifecycle façade used by Kotlin/Native.
@objc(WeftLiveActivityBridge)
public final class WeftLiveActivityBridge: NSObject {
    private let driver: WeftLiveActivityDriver
    private let tailLock = NSLock()
    private var tail: Task<Void, Never>?

    @objc public override convenience init() {
        self.init(driver: makeDefaultDriver())
    }

    internal init(driver: WeftLiveActivityDriver) {
        self.driver = driver
        super.init()
    }

    @objc public func availability() -> String {
        driver.availabilityCode
    }

    @objc(startWithId:title:status:subtitle:progressCurrent:progressTotal:startedAtMillis:endsAtMillis:deepLink:completion:)
    public func start(
        id: String,
        title: String,
        status: String,
        subtitle: String?,
        progressCurrent: NSNumber?,
        progressTotal: NSNumber?,
        startedAtMillis: NSNumber?,
        endsAtMillis: NSNumber?,
        deepLink: String?,
        completion: @escaping (WeftLiveActivityBridgeResult) -> Void,
    ) {
        do {
            guard status == "active" else { throw BridgeFailure.invalidState("start requires active status") }
            let snapshot = try makeSnapshot(
                title: title,
                status: status,
                subtitle: subtitle,
                progressCurrent: progressCurrent,
                progressTotal: progressTotal,
                startedAtMillis: startedAtMillis,
                endsAtMillis: endsAtMillis,
                deepLink: deepLink,
            )
            enqueue(completion: completion) { [self] in
                try await start(snapshot: snapshot, id: id)
            }
        } catch {
            deliver(result(for: error), completion: completion)
        }
    }

    @objc(updateWithId:title:status:subtitle:progressCurrent:progressTotal:startedAtMillis:endsAtMillis:deepLink:completion:)
    public func update(
        id: String,
        title: String,
        status: String,
        subtitle: String?,
        progressCurrent: NSNumber?,
        progressTotal: NSNumber?,
        startedAtMillis: NSNumber?,
        endsAtMillis: NSNumber?,
        deepLink: String?,
        completion: @escaping (WeftLiveActivityBridgeResult) -> Void,
    ) {
        do {
            guard status == "active" else { throw BridgeFailure.invalidState("update requires active status") }
            let snapshot = try makeSnapshot(
                title: title,
                status: status,
                subtitle: subtitle,
                progressCurrent: progressCurrent,
                progressTotal: progressTotal,
                startedAtMillis: startedAtMillis,
                endsAtMillis: endsAtMillis,
                deepLink: deepLink,
            )
            enqueue(completion: completion) { [self] in
                try await update(snapshot: snapshot, id: id)
            }
        } catch {
            deliver(result(for: error), completion: completion)
        }
    }

    @objc(endWithId:title:status:subtitle:progressCurrent:progressTotal:startedAtMillis:endsAtMillis:deepLink:completion:)
    public func end(
        id: String,
        title: String,
        status: String,
        subtitle: String?,
        progressCurrent: NSNumber?,
        progressTotal: NSNumber?,
        startedAtMillis: NSNumber?,
        endsAtMillis: NSNumber?,
        deepLink: String?,
        completion: @escaping (WeftLiveActivityBridgeResult) -> Void,
    ) {
        do {
            guard ["completed", "failed", "cancelled"].contains(status) else {
                throw BridgeFailure.invalidState("end requires a terminal status")
            }
            let snapshot = try makeSnapshot(
                title: title,
                status: status,
                subtitle: subtitle,
                progressCurrent: progressCurrent,
                progressTotal: progressTotal,
                startedAtMillis: startedAtMillis,
                endsAtMillis: endsAtMillis,
                deepLink: deepLink,
            )
            enqueue(completion: completion) { [self] in
                try await end(snapshot: snapshot, id: id, cancelled: status == "cancelled")
            }
        } catch {
            deliver(result(for: error), completion: completion)
        }
    }

    private func enqueue(
        completion: @escaping (WeftLiveActivityBridgeResult) -> Void,
        operation: @escaping () async throws -> Void,
    ) {
        tailLock.lock()
        let previous = tail
        let next = Task { @MainActor [self] in
            if let previous {
                await previous.value
            }
            do {
                try await operation()
                deliver(.success, completion: completion)
            } catch {
                deliver(result(for: error), completion: completion)
            }
        }
        tail = next
        tailLock.unlock()
    }

    private func start(snapshot: WeftLiveActivitySnapshot, id: String) async throws {
        let matches = matchingActivities(id: id)
        switch matches.count {
        case 0:
            try await driver.request(id: id, state: snapshot)
        case 1 where isActiveLike(matches[0].state):
            try await driver.update(id: id, state: snapshot)
        case 1:
            throw BridgeFailure.invalidState("start found a non-active activity for id '\(id)'")
        default:
            throw BridgeFailure.platformFailure("multiple activities found for id '\(id)'")
        }
    }

    private func update(snapshot: WeftLiveActivitySnapshot, id: String) async throws {
        let matches = matchingActivities(id: id)
        guard matches.count == 1, isActiveLike(matches[0].state) else {
            throw BridgeFailure.invalidState("update requires one active activity for id '\(id)'")
        }
        try await driver.update(id: id, state: snapshot)
    }

    private func end(snapshot: WeftLiveActivitySnapshot, id: String, cancelled: Bool) async throws {
        let matches = matchingActivities(id: id)
        guard matches.count == 1, isActiveLike(matches[0].state) else {
            throw BridgeFailure.invalidState("end requires one active activity for id '\(id)'")
        }
        try await driver.end(id: id, state: snapshot, cancelled: cancelled)
    }

    private func matchingActivities(id: String) -> [WeftLiveActivityRecord] {
        driver.activities().filter { $0.id == id }
    }

    private func isActiveLike(_ state: String) -> Bool {
        let normalized = state.lowercased()
        return normalized == "active" || normalized == "stale"
    }

    private func makeSnapshot(
        title: String,
        status: String,
        subtitle: String?,
        progressCurrent: NSNumber?,
        progressTotal: NSNumber?,
        startedAtMillis: NSNumber?,
        endsAtMillis: NSNumber?,
        deepLink: String?,
    ) throws -> WeftLiveActivitySnapshot {
        guard !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw BridgeFailure.invalidState("title must not be blank")
        }
        guard (progressCurrent == nil) == (progressTotal == nil) else {
            throw BridgeFailure.invalidState("progress current and total must be provided together")
        }

        let current = progressCurrent?.int64Value
        let total = progressTotal?.int64Value
        if let current, let total, (total <= 0 || current < 0 || current > total) {
            throw BridgeFailure.invalidState("progress values are out of bounds")
        }
        let started = startedAtMillis?.int64Value
        let ends = endsAtMillis?.int64Value
        if let started, started < 0 { throw BridgeFailure.invalidState("start time must be non-negative") }
        if let ends, ends < 0 { throw BridgeFailure.invalidState("end time must be non-negative") }
        if let started, let ends, ends < started {
            throw BridgeFailure.invalidState("end time must not precede start time")
        }
        if status == "completed", let current, let total, current != total {
            throw BridgeFailure.invalidState("completed progress must be full")
        }
        if let deepLink, !isValidDeepLink(deepLink) {
            throw BridgeFailure.invalidState("deepLink must be an absolute hierarchical URL")
        }

        let snapshot = WeftLiveActivitySnapshot(
            title: title,
            subtitle: subtitle,
            status: status,
            progressCurrent: current,
            progressTotal: total,
            startedAtMillis: started,
            endsAtMillis: ends,
            deepLink: deepLink,
        )
        guard encodedPayloadSize(snapshot) <= 4096 else {
            throw BridgeFailure.payloadTooLarge
        }
        return snapshot
    }

    private func deliver(
        _ result: WeftLiveActivityBridgeResult,
        completion: @escaping (WeftLiveActivityBridgeResult) -> Void,
    ) {
        if Thread.isMainThread {
            completion(result)
        } else {
            DispatchQueue.main.async {
                completion(result)
            }
        }
    }

    private func result(for error: Error) -> WeftLiveActivityBridgeResult {
        switch error {
        case let failure as BridgeFailure:
            return failure.result
        case WeftLiveActivityDriverError.unsupported:
            return .failure(code: "unsupported", message: "ActivityKit is unsupported on this platform")
        case WeftLiveActivityDriverError.disabled:
            return .failure(code: "disabled", message: "Live Activities are disabled")
        case WeftLiveActivityDriverError.invalidState:
            return .failure(code: "invalid_state", message: "ActivityKit rejected the activity state")
        case WeftLiveActivityDriverError.notFound:
            return .failure(code: "invalid_state", message: "The requested live activity was not found")
        default:
            return .failure(code: "platform_failure", message: "ActivityKit operation failed: \(error.localizedDescription)")
        }
    }

    private static let success = WeftLiveActivityBridgeResult(accepted: true)
}

private enum BridgeFailure: Error {
    case invalidState(String)
    case payloadTooLarge
    case platformFailure(String)

    var result: WeftLiveActivityBridgeResult {
        switch self {
        case let .invalidState(message):
            return .failure(code: "invalid_state", message: message)
        case .payloadTooLarge:
            return .failure(code: "payload_too_large", message: "ActivityKit content state exceeds 4,096 UTF-8 bytes")
        case let .platformFailure(message):
            return .failure(code: "platform_failure", message: message)
        }
    }
}

private extension WeftLiveActivityBridgeResult {
    static let success = WeftLiveActivityBridgeResult(accepted: true)

    static func failure(code: String, message: String) -> WeftLiveActivityBridgeResult {
        WeftLiveActivityBridgeResult(accepted: false, failureCode: code, failureMessage: message)
    }
}

internal func encodedPayloadSize(_ snapshot: WeftLiveActivitySnapshot) -> Int {
    (try? JSONEncoder().encode(snapshot).count) ?? Int.max
}

private func isValidDeepLink(_ value: String) -> Bool {
    guard !value.isEmpty,
          value.utf8.count <= 2048,
          !value.contains(where: { $0.isWhitespace || $0.isNewline || $0 == "\\" }),
          let components = URLComponents(string: value),
          let scheme = components.scheme,
          !scheme.isEmpty,
          let host = components.host,
          !host.isEmpty,
          URL(string: value) != nil else {
        return false
    }
    return scheme.first?.isLetter == true
}

private func makeDefaultDriver() -> WeftLiveActivityDriver {
#if os(iOS) && canImport(ActivityKit)
    ActivityKitDriver()
#else
    UnsupportedDriver()
#endif
}

private final class UnsupportedDriver: WeftLiveActivityDriver {
    var availabilityCode: String { "unsupported" }
    func activities() -> [WeftLiveActivityRecord] { [] }
    func request(id: String, state: WeftLiveActivitySnapshot) async throws { throw WeftLiveActivityDriverError.unsupported }
    func update(id: String, state: WeftLiveActivitySnapshot) async throws { throw WeftLiveActivityDriverError.unsupported }
    func end(id: String, state: WeftLiveActivitySnapshot, cancelled: Bool) async throws { throw WeftLiveActivityDriverError.unsupported }
}

#if os(iOS) && canImport(ActivityKit)
@available(iOS 16.1, *)
public struct WeftLiveActivityAttributes: ActivityAttributes {
    public typealias ContentState = WeftLiveActivitySnapshot
    public let id: String

    public init(id: String) {
        self.id = id
    }
}

@available(iOS 16.1, *)
private final class ActivityKitDriver: WeftLiveActivityDriver {
    var availabilityCode: String {
        return ActivityAuthorizationInfo().areActivitiesEnabled ? "available" : "disabled"
    }

    func activities() -> [WeftLiveActivityRecord] {
        return Activity<WeftLiveActivityAttributes>.activities.map {
            WeftLiveActivityRecord(
                id: $0.attributes.id,
                state: String(describing: $0.activityState).lowercased(),
            )
        }
    }

    func request(id: String, state: WeftLiveActivitySnapshot) async throws {
        do {
            _ = try Activity<WeftLiveActivityAttributes>.request(
                attributes: WeftLiveActivityAttributes(id: id),
                contentState: state,
                pushType: nil,
            )
        } catch let error as ActivityAuthorizationError {
            throw map(error)
        } catch {
            throw WeftLiveActivityDriverError.platformFailure
        }
    }

    func update(id: String, state: WeftLiveActivitySnapshot) async throws {
        guard let activity = Activity<WeftLiveActivityAttributes>.activities.first(where: { $0.attributes.id == id }) else {
            throw WeftLiveActivityDriverError.notFound
        }
        await activity.update(using: state)
    }

    func end(id: String, state: WeftLiveActivitySnapshot, cancelled: Bool) async throws {
        guard let activity = Activity<WeftLiveActivityAttributes>.activities.first(where: { $0.attributes.id == id }) else {
            throw WeftLiveActivityDriverError.notFound
        }
        await activity.end(using: state, dismissalPolicy: cancelled ? .immediate : .default)
    }

    private func map(_ error: ActivityAuthorizationError) -> WeftLiveActivityDriverError {
        switch error {
        case .unsupported:
            return .unsupported
        case .denied:
            return .disabled
        default:
            return .platformFailure
        }
    }
}
#endif

#if os(iOS) && canImport(ActivityKit) && canImport(WidgetKit) && canImport(SwiftUI)
@available(iOS 16.1, *)
public struct WeftLiveActivityWidget: Widget {
    public init() {}

    public var body: some WidgetConfiguration {
        ActivityConfiguration(for: WeftLiveActivityAttributes.self) { context in
            WeftLiveActivityLockScreenView(state: context.state)
                .widgetURL(context.state.deepLink.flatMap(URL.init))
                .activityBackgroundTint(.black)
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    WeftLiveActivityText(context.state.title, label: "Title")
                }
                DynamicIslandExpandedRegion(.trailing) {
                    WeftLiveActivityText(context.state.status.capitalized, label: "Status")
                }
                DynamicIslandExpandedRegion(.bottom) {
                    WeftLiveActivityDetails(state: context.state)
                }
            } compactLeading: {
                WeftLiveActivityText(context.state.title, label: "Title")
            } compactTrailing: {
                WeftLiveActivityText(context.state.status.capitalized, label: "Status")
            } minimal: {
                WeftLiveActivityText(context.state.status.capitalized, label: "Status")
            }
        }
    }
}

@available(iOS 16.1, *)
private struct WeftLiveActivityLockScreenView: View {
    let state: WeftLiveActivitySnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            WeftLiveActivityText(state.title, label: "Title")
            if let subtitle = state.subtitle, !subtitle.isEmpty {
                WeftLiveActivityText(subtitle, label: "Subtitle")
            }
            WeftLiveActivityText(state.status.capitalized, label: "Status")
            WeftLiveActivityDetails(state: state)
        }
        .padding()
    }
}

@available(iOS 16.1, *)
private struct WeftLiveActivityDetails: View {
    let state: WeftLiveActivitySnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let current = state.progressCurrent, let total = state.progressTotal {
                ProgressView(value: Double(current), total: Double(total))
                    .accessibilityLabel("Progress \(current) of \(total)")
                Text("\(current)/\(total)")
                    .accessibilityLabel("Progress \(current) of \(total)")
            }
            if state.status == "active", let started = state.startedAtMillis {
                Text(Date(timeIntervalSince1970: Double(started) / 1000), style: .timer)
                    .accessibilityLabel("Elapsed time")
            }
            if state.status == "active", let ends = state.endsAtMillis {
                Text(Date(timeIntervalSince1970: Double(ends) / 1000), style: .time)
                    .accessibilityLabel("Estimated completion time")
            }
        }
    }
}

@available(iOS 16.1, *)
private struct WeftLiveActivityText: View {
    let value: String
    let label: String

    init(_ value: String, label: String) {
        self.value = value
        self.label = label
    }

    var body: some View {
        Text(value).accessibilityLabel("\(label): \(value)")
    }
}
#endif
