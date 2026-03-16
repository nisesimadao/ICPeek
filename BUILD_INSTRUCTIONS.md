# ICPeek KMP ビルド手順

## 前提条件

### 環境要件
- **Java**: 17+ (現在Java 25で動作確認済み)
- **Android Studio**: Giraffe (2022.3.1) 以降
- **Xcode**: 15.0+ (iOSビルド用)
- **Kotlin**: 2.0.0
- **Gradle**: 8.11.1

### SDK要件
- **Android SDK**: API 29+ (最小), API 34 (ターゲット)
- **iOS**: iOS 16.0+ (最小)

## ビルド手順

### 1. 共有モジュールのビルド

```bash
# プロジェクトルートで実行
./gradlew :shared:build
```

Java 25環境の場合は以下のgradle.properties設定が必要：
```
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED
```

### 2. Androidアプリのビルド

#### Android Studioでのビルド
1. Android Studioでプロジェクトを開く
2. `app` モジュールを選択
3. `Build` → `Make Project` または `Build APK(s)`

#### コマンドラインでのビルド
```bash
# デバッグビルド
./gradlew :app:assembleDebug

# リリースビルド
./gradlew :app:assembleRelease
```

### 3. iOSアプリのビルド

#### iOSフレームワークのビルド
```bash
# iOS用フレームワークをビルド
./gradlew :shared:embedAndSignAppleFrameworkForXcode

# またはスクリプトを使用
./build_ios_framework.sh
```

#### Xcodeでのビルド
1. `iosApp/ICPeek.xcodeproj` を開く
2. ビルドターゲットを選択 (Simulator/Device)
3. `Product` → `Build` または `Run`

## トラブルシューティング

### Javaバージョン関連エラー
```
Unsupported class file major version 69
```

**解決策**:
1. gradle.propertiesに以下を追加:
   ```
   org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED
   ```
2. Gradleデーモンを再起動:
   ```bash
   ./gradlew --stop
   ```

### Androidビルドエラー
**最小SDK問題**: API 29未満のデバイスでは動作しません
**NFC権限**: AndroidManifest.xmlでNFC権限を確認

### iOSビルドエラー
**CoreNFC**: Info.plistでNFC利用を許可
**署名**: 開発者証明書を設定

## プロジェクト構成

### 共有モジュール (`shared/`)
- `commonMain/kotlin/`: プラットフォーム共通コード
- `androidMain/kotlin/`: Android固有実装
- `iosMain/kotlin/`: iOS固有実装

### Androidアプリ (`app/`)
- API 29+専用のReaderMode実装
- Material Design Components
- 共有モジュール依存

### iOSアプリ (`iosApp/`)
- SwiftUIベース
- CoreNFC使用
- 共有フレームワークリンク

## テスト

### Androidテスト
```bash
./gradlew :app:connectedAndroidTest
```

### iOSテスト
Xcodeでテストターゲットを実行

## デプロイ

### Android
1. リリースビルドを作成
2. Google Play Consoleにアップロード

### iOS
1. App Store用にビルド
2. App Store Connectにアップロード

## 機能確認

### 対応カード
- ICOCA, Suica, PASMO
- Edy, nanaco, WAON
- その他FeliCaベースカード

### テスト手順
1. NFC対応デバイスを準備
2. 対応ICカードを用意
3. アプリを起動してカードをかざす
4. 残高と取引履歴が表示されることを確認
