import Foundation
import UserNotifications
import WeftPush

final class WeftNotificationServiceExtension: UNNotificationServiceExtension {
    private let processor = FirebasePushNotificationProcessor()
    private var contentHandler: ((UNNotificationContent) -> Void)?
    private var bestAttemptContent: UNMutableNotificationContent?

    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        self.contentHandler = contentHandler
        guard let content = request.content.mutableCopy() as? UNMutableNotificationContent else {
            complete(request.content)
            return
        }
        bestAttemptContent = content
        _ = processor.process(userInfo: request.content.userInfo, content: content)
        complete(content)
    }

    override func serviceExtensionTimeWillExpire() {
        complete(bestAttemptContent ?? UNMutableNotificationContent())
    }

    private func complete(_ content: UNNotificationContent) {
        guard let contentHandler else { return }
        self.contentHandler = nil
        contentHandler(content)
    }
}
