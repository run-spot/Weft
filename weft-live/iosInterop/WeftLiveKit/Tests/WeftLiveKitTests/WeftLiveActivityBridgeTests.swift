import XCTest
@testable import WeftLiveKit

final class WeftLiveActivityBridgeTests: XCTestCase {
    func testAvailabilityAndInvalidOperationAreMapped() async {
        let driver = FakeDriver()
        let bridge = WeftLiveActivityBridge(driver: driver)

        XCTAssertEqual(bridge.availability(), "available")

        let invalid = await startResult(bridge, status: "completed")
        XCTAssertFalse(invalid.accepted)
        XCTAssertEqual(invalid.failureCode, "invalid_state")
        XCTAssertEqual(driver.requestCount, 0)
    }

    func testDisabledAndUnsupportedAvailabilityAreExposed() {
        let driver = FakeDriver()
        let bridge = WeftLiveActivityBridge(driver: driver)

        driver.availabilityCode = "disabled"
        XCTAssertEqual(bridge.availability(), "disabled")
        driver.availabilityCode = "unsupported"
        XCTAssertEqual(bridge.availability(), "unsupported")
    }

    func testStartCreatesAndThenUpdatesAnActiveActivity() async {
        let driver = FakeDriver()
        let bridge = WeftLiveActivityBridge(driver: driver)

        let created = await startResult(bridge, title: "Preparing")
        XCTAssertTrue(created.accepted)
        XCTAssertEqual(driver.requestCount, 1)

        driver.records = [WeftLiveActivityRecord(id: "order", state: "active")]
        let updated = await startResult(bridge, title: "Almost ready")
        XCTAssertTrue(updated.accepted)
        XCTAssertEqual(driver.updateCount, 1)
    }

    func testUpdateAndEndRequireOneActiveActivity() async {
        let driver = FakeDriver()
        let bridge = WeftLiveActivityBridge(driver: driver)

        let unknownUpdate = await updateResult(bridge)
        XCTAssertFalse(unknownUpdate.accepted)
        XCTAssertEqual(unknownUpdate.failureCode, "invalid_state")

        let unknownEnd = await endResult(bridge, status: "cancelled")
        XCTAssertFalse(unknownEnd.accepted)
        XCTAssertEqual(unknownEnd.failureCode, "invalid_state")

        driver.records = [WeftLiveActivityRecord(id: "order", state: "active")]
        let cancelled = await endResult(bridge, status: "cancelled")
        XCTAssertTrue(cancelled.accepted)
        XCTAssertEqual(driver.endCount, 1)
        XCTAssertTrue(driver.lastEndWasCancelled)
    }

    func testExactPayloadLimitIsAcceptedAndOneByteOverIsRejected() async {
        let driver = FakeDriver()
        let bridge = WeftLiveActivityBridge(driver: driver)
        let base = makeSnapshot(subtitle: "")
        let exactSubtitle = String(repeating: "a", count: 4096 - encodedPayloadSize(base))
        let exact = makeSnapshot(subtitle: exactSubtitle)
        XCTAssertEqual(encodedPayloadSize(exact), 4096)

        let accepted = await startResult(bridge, subtitle: exactSubtitle)
        XCTAssertTrue(accepted.accepted)

        let rejected = await startResult(bridge, subtitle: exactSubtitle + "a")
        XCTAssertFalse(rejected.accepted)
        XCTAssertEqual(rejected.failureCode, "payload_too_large")
        XCTAssertEqual(driver.requestCount, 1)
    }

    func testNativeFailuresAreMappedAndCompletionIsDeliveredOnce() async {
        let driver = FakeDriver()
        driver.failure = FakeError.nativeFailure
        let bridge = WeftLiveActivityBridge(driver: driver)

        var completionCount = 0
        let result = await withCheckedContinuation { continuation in
            bridge.start(
                id: "order",
                title: "Preparing",
                status: "active",
                subtitle: nil,
                progressCurrent: 1,
                progressTotal: 4,
                startedAtMillis: 100,
                endsAtMillis: 200,
                deepLink: "weft://orders/order",
            ) { result in
                completionCount += 1
                if completionCount == 1 {
                    continuation.resume(returning: result)
                }
            }
        }
        XCTAssertFalse(result.accepted)
        XCTAssertEqual(result.failureCode, "platform_failure")
        XCTAssertFalse(result.failureMessage?.isEmpty ?? true)
        for _ in 0..<10 { await Task.yield() }
        XCTAssertEqual(completionCount, 1)
    }

    func testDriverFailureCodesAreMapped() async {
        let failures: [(Error, String)] = [
            (WeftLiveActivityDriverError.unsupported, "unsupported"),
            (WeftLiveActivityDriverError.disabled, "disabled"),
            (WeftLiveActivityDriverError.invalidState, "invalid_state"),
            (WeftLiveActivityDriverError.notFound, "invalid_state"),
        ]

        for (failure, code) in failures {
            let driver = FakeDriver()
            driver.failure = failure
            let bridge = WeftLiveActivityBridge(driver: driver)
            let result = await startResult(bridge)
            XCTAssertFalse(result.accepted)
            XCTAssertEqual(result.failureCode, code)
            XCTAssertFalse(result.failureMessage?.isEmpty ?? true)
        }
    }

    func testQueuedOperationsDoNotOvertakeAnEarlierNativeOperation() async {
        let driver = FakeDriver()
        driver.holdFirstRequest = true
        let bridge = WeftLiveActivityBridge(driver: driver)

        let firstTask = Task { await startResult(bridge, title: "First") }
        for _ in 0..<100 where !driver.firstRequestStarted {
            await Task.yield()
        }
        XCTAssertTrue(driver.firstRequestStarted)

        let secondTask = Task { await startResult(bridge, title: "Second") }
        for _ in 0..<20 {
            await Task.yield()
        }
        XCTAssertEqual(driver.requestCount, 1)

        driver.releaseFirstRequest()
        let first = await firstTask.value
        let second = await secondTask.value
        XCTAssertTrue(first.accepted)
        XCTAssertTrue(second.accepted)
        XCTAssertEqual(driver.requestCount, 2)
    }

    private func startResult(
        _ bridge: WeftLiveActivityBridge,
        id: String = "order",
        title: String = "Preparing",
        status: String = "active",
        subtitle: String? = nil,
    ) async -> WeftLiveActivityBridgeResult {
        await withCheckedContinuation { continuation in
            bridge.start(
                id: id,
                title: title,
                status: status,
                subtitle: subtitle,
                progressCurrent: 1,
                progressTotal: 4,
                startedAtMillis: 100,
                endsAtMillis: 200,
                deepLink: "weft://orders/order",
            ) { result in
                continuation.resume(returning: result)
            }
        }
    }

    private func updateResult(_ bridge: WeftLiveActivityBridge) async -> WeftLiveActivityBridgeResult {
        await withCheckedContinuation { continuation in
            bridge.update(
                id: "order",
                title: "Update",
                status: "active",
                subtitle: nil,
                progressCurrent: 2,
                progressTotal: 4,
                startedAtMillis: 100,
                endsAtMillis: 200,
                deepLink: nil,
            ) { result in
                continuation.resume(returning: result)
            }
        }
    }

    private func endResult(
        _ bridge: WeftLiveActivityBridge,
        status: String,
    ) async -> WeftLiveActivityBridgeResult {
        await withCheckedContinuation { continuation in
            bridge.end(
                id: "order",
                title: "Finished",
                status: status,
                subtitle: nil,
                progressCurrent: 4,
                progressTotal: 4,
                startedAtMillis: 100,
                endsAtMillis: 200,
                deepLink: nil,
            ) { result in
                continuation.resume(returning: result)
            }
        }
    }

    private func makeSnapshot(subtitle: String?) -> WeftLiveActivitySnapshot {
        WeftLiveActivitySnapshot(
            title: "Preparing",
            subtitle: subtitle,
            status: "active",
            progressCurrent: 1,
            progressTotal: 4,
            startedAtMillis: 100,
            endsAtMillis: 200,
            deepLink: "weft://orders/order",
        )
    }
}

private final class FakeDriver: WeftLiveActivityDriver {
    var availabilityCode = "available"
    var records: [WeftLiveActivityRecord] = []
    var failure: Error?
    var requestCount = 0
    var updateCount = 0
    var endCount = 0
    var lastEndWasCancelled = false
    var holdFirstRequest = false
    var firstRequestStarted = false
    private var firstRequestContinuation: CheckedContinuation<Void, Never>?

    func activities() -> [WeftLiveActivityRecord] { records }

    func request(id: String, state: WeftLiveActivitySnapshot) async throws {
        try throwFailureIfNeeded()
        requestCount += 1
        if holdFirstRequest && requestCount == 1 {
            firstRequestStarted = true
            await withCheckedContinuation { continuation in
                firstRequestContinuation = continuation
            }
        }
    }

    func update(id: String, state: WeftLiveActivitySnapshot) async throws {
        try throwFailureIfNeeded()
        updateCount += 1
    }

    func end(id: String, state: WeftLiveActivitySnapshot, cancelled: Bool) async throws {
        try throwFailureIfNeeded()
        endCount += 1
        lastEndWasCancelled = cancelled
    }

    private func throwFailureIfNeeded() throws {
        if let failure { throw failure }
    }

    func releaseFirstRequest() {
        firstRequestContinuation?.resume()
        firstRequestContinuation = nil
    }
}

private enum FakeError: Error {
    case nativeFailure
}
