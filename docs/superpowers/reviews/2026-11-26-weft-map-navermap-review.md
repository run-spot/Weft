# Weft Naver Map Provider Code Review

## Review Scope

- **Base:** `24d763274ee95a98a539b1de1e8804b66dd2c156` (merge-base for `origin/main...HEAD`)
- **Upstream ref:** `origin/main` at `8097ff864dee976549b8f28bf289caff177c80c6`
- **Source head:** `06b3fe9d7be0b4a9fde69414c71cc600940d070a` plus the final Android key-redaction fix committed with this report.
- **Changed paths:** the feature branch includes Naver-provider implementation, build, documentation, and consumer-fixture paths. The validation/fix delta includes the generated Naver SwiftPM wrapper package and Android/iOS validation fixes; the final follow-up changes only the Android error wrapper, its regression test, and this report. No build outputs or vendor checkouts are tracked.

## Review Findings and Disposition

### Earlier Android lifecycle/overlay review

- **Important — Android overlay identity, rollback, and teardown failures:** closed in `da4da9a` and `693ad9d`. The coordinator/reconciler tests cover duplicate IDs, rollback, and cleanup failures.

### Final review after validation fixes

- **Important — generated SwiftPM dependency was not included:** `.swiftpm-locks/default/swiftImport/Package.swift` referenced `_weft-map_navermap`, while the generated leaf manifest and Objective-C wrapper files were untracked. Added all four wrapper files alongside the root manifest. Root and leaf manifests parse, and the clean re-review confirmed the finding is resolved.
- **Important — Android initializer cause could expose the configured key:** the raw SDK initialization throwable was attached as the public `NaverMapException` cause, so its message/stack trace could contain the NCP key. Resolved by discarding that throwable and delivering only the constant-message exception. The regression now injects a key-bearing failure and asserts the callback exception has no cause and its message/stack trace do not contain the key.
- iOS error suppression resets after a successful recovery so a later failure is reported; the coordinator regression test covers recovery followed by another failure.
- **Critical findings:** None unresolved.
- **Important findings:** None unresolved.
- **Minor findings:** None in the scoped final re-review.
- **Final re-review:** CLEAN after the Android key-redaction fix. The scoped re-review confirmed the original Important finding is resolved and found no additional Critical/Important/Minor issue.

## Verification

- `git fetch --all --prune` — passed.
- `git diff --check origin/main...HEAD`, `git diff --cached --check`, and `git diff --check origin/main` — passed.
- `swift package dump-package --package-path .swiftpm-locks/default/swiftImport` and `swift package dump-package --package-path .swiftpm-locks/default/swiftImport/subpackages/_weft-map_navermap` — passed; generated wrapper files are present.
- `ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew --rerun-tasks :weft-map:navermap:testAndroidHostTest :weft-map:navermap:compileTestKotlinIosSimulatorArm64` — passed (46 tasks executed).
- `./scripts/verify.sh` — passed.
- `ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew :weft-core:check :weft-map:kakaomap:check :weft-map:navermap:check verifyNaverMapPublication verifyWeftBomPublication verify` — passed (203 actionable tasks; 1 from cache, 135 up-to-date).
- `ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew :weft-map:navermap:compileKotlinIosSimulatorArm64 :weft-map:navermap:compileKotlinIosArm64 :weft-map:navermap:linkDebugFrameworkIosSimulatorArm64 :weft-map:navermap:linkDebugFrameworkIosArm64` — passed.
- Focused TDD regression: `ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew :weft-map:navermap:testAndroidHostTest --tests 'run.thespot.weft.map.navermap.NaverMapAndroidCoordinatorTest.reportsSdkFactoryFailureThroughOnErrorAndReturnsPlaceholderView'` — failed first at the new no-cause assertion with the raw initializer throwable attached; passed after the fix. It passed again after the final cleanup.
- Final affected/full checks after the fix: `ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew :weft-core:check :weft-map:kakaomap:check :weft-map:navermap:check verifyNaverMapPublication verifyWeftBomPublication verify` — passed (203 actionable tasks; 68 executed, 135 up-to-date).
- `XCODEPROJ_PATH="$PWD/harness/fixtures/weft-navermap-ios-consumer/WeftNaverConsumer.xcodeproj" ./gradlew :weft-map:navermap:integrateLinkagePackage` — passed; linkage product was already referenced.
- `xcodebuild -project harness/fixtures/weft-navermap-ios-consumer/WeftNaverConsumer.xcodeproj -scheme WeftNaverConsumer -sdk iphonesimulator build` — passed.
- Consumer bundle checks passed: `NMapsMap.framework/NMapsMap` and `Assets.car` exist; app and `WeftNaverMap` minimum OS are 13.0; `NMapsMap` minimum OS is 9.0.
- iOS simulator runtime tests were skipped on the `macos_x64` host; simulator/device Kotlin compilation and framework linking plus the Xcode consumer build passed.
- Final path inspection: generated Naver SwiftPM wrapper files are tracked; the follow-up commit contains only the Android implementation/test and this report. No generated build outputs or vendor checkouts are tracked.

## Assessment

The Naver Maps implementation and consumer linkage are ready for PR review. The feature branch is 26 commits ahead and 1 commit behind the fetched `origin/main` after this follow-up commit; no rebase, publication, or merge was performed.
