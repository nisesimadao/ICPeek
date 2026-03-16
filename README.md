# ICPeek - ICカード残高表示アプリ (Kotlin Multiplatform)

FeliCa方式の交通系ICカードの残高と取引履歴をNFCで読み取るマルチプラットフォームアプリケーション。AndroidとiOSに対応しています。

## 🌟 プラットフォーム対応

- **Android**: API 29+ (Android 10+) - ReaderMode専用実装
- **iOS**: iOS 16.0+ - SwiftUI + CoreNFC実装
- **共有コード**: Kotlin Multiplatformでコアロジックを共有

## 🎴 対応カード

- **ICOCA** - JR西日本
- **Suica** - JR東日本  
- **PASMO** - 関東私鉄
- **Edy** - 電子マネー
- **nanaco** - セブン-イレブン
- **WAON** - イオン
- その他FeliCaベースの交通系ICカード


## 🚀 主な機能

### ✅ 基本機能
- **NFCカード検出** - 自動でカードを認識
- **残高表示** - 現在の残高をリアルタイム表示
- **カード種類検出** - カードタイプを自動判定して色分け表示
- **FeliCa通信** - 安定したカードとの通信

### ✅ 取引履歴機能
- **詳細な取引履歴** - 最新の取引を最大10件表示
- **増減額表示** - 取引による増減を色分け（緑：増加、赤：減少）
- **取引タイプ表示** - 物販、乗車、精算などの詳細
- **クリックで詳細** - 取引をタップでRawデータを確認

### ✅ UI/UX
- **Android**: Material Designベースのクリーンなデザイン
- **iOS**: SwiftUIベースのモダンなインターフェース
- **カード種類別カラー** - ICOCA（青）、Suica/PASMO（緑）など
- **レスポンシブレイアウト** - 画面サイズに最適化
- **日本語対応** - 完全日本語UI

## 📋 動作要件

### Android
- **端末**: NFC機能搭載のAndroid端末
- **バージョン**: API 29 (Android 10) 以上
- **NFC**: NFC機能が有効になっていること

### iOS
- **端末**: iPhone 7以降 (NFC対応)
- **バージョン**: iOS 16.0 以上
- **NFC**: CoreNFC対応

### 共通
- **FeliCa対応ICカード**: 対応カードをご用意ください

## � ビルド方法

### 前提条件
- **Java**: 17+ (Java 25で動作確認済み)
- **Android Studio**: Giraffe (2022.3.1) 以降
- **Xcode**: 15.0+ (iOSビルド用)
- **Kotlin**: 2.0.0

### 共有モジュールのビルド
```bash
# プロジェクトルートで実行
./gradlew :shared:build
```

### Androidアプリのビルド
```bash
# デバッグビルド
./gradlew :app:assembleDebug

# リリースビルド
./gradlew :app:assembleRelease
```

### iOSアプリのビルド
```bash
# iOS用フレームワークをビルド
./gradlew :shared:embedAndSignAppleFrameworkForXcode

# Xcodeでプロジェクトを開く
open iosApp/ICPeek.xcodeproj
```

詳細なビルド手順は [BUILD_INSTRUCTIONS.md](BUILD_INSTRUCTIONS.md) を参照してください。

## 🏗️ プロジェクト構造

```
ICPeek/
├── shared/                    # 共有モジュール
│   ├── src/
│   │   ├── commonMain/kotlin/ # プラットフォーム共通コード
│   │   ├── androidMain/kotlin/ # Android固有実装
│   │   └── iosMain/kotlin/     # iOS固有実装
│   └── build.gradle.kts
├── app/                       # Androidアプリ
│   └── src/main/java/com/icpeek/app/
├── iosApp/                    # iOSアプリ
│   └── ICPeek/
├── BUILD_INSTRUCTIONS.md      # 詳細ビルド手順
├── DEVELOPMENT_NOTES.md       # 開発メモ
└── README_KMP.md             # KMP版説明
```

## 🔧 技術仕様

### 共有モジュール
- **言語**: Kotlin 2.0.0
- **シリアライゼーション**: Kotlinx Serialization
- **非同期処理**: Kotlinx Coroutines

### Android
- **最小SDK**: API 29
- **ターゲットSDK**: API 34
- **NFC**: ReaderMode API専用
- **UI**: Material Design Components
- **アーキテクチャ**: MVP + Coroutines

### iOS
- **最小バージョン**: iOS 16.0
- **UI**: SwiftUI
- **NFC**: CoreNFC
- **アーキテクチャ**: MVVM
- **共有コード**: Kotlin/Native Framework

### NFC技術
- **FeliCa**: Read Without Encryptionコマンド
- **サービスコード**: 0x090F (残高読み取り)
- **データ解析**: 16バイトブロックのリトルエンディアン

## 📱 使い方

### Android
1. **アプリインストール** - APKからインストール
2. **NFC有効化** - 設定でNFCをオンにする
3. **アプリ起動** - ICPeekを起動
4. **カードタップ** - ICカードを端末のNFCリーダーに近づける
5. **情報表示** - 残高と取引履歴が自動表示

### iOS
1. **アプリインストール** - Xcodeからビルド＆インストール
2. **アプリ起動** - ICPeekを起動
3. **カード読み取り** - 「カード読み取り」ボタンをタップ
4. **カードタップ** - ICカードをiPhoneの上部に近づける
5. **情報表示** - 残高と取引履歴が自動表示

## 🎯 今後の拡張機能

- [ ] **Web版実装** - Kotlin/Wasm
- [ ] **Desktop版実装** - Compose Desktop
- [ ] **クラウド同期** - 取引データのバックアップ
- [ ] **カード管理** - 複数カードの登録・管理
- [ ] **ウィジェット対応** - ホーム画面ウィジェット
- [ ] **Apple Watch対応** - watchOSアプリ

## � トラブルシューティング

### Java 25 環境でのビルドエラー
```
Unsupported class file major version 69
```
**解決策**: gradle.propertiesに以下を設定済み:
```
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED
```

### AndroidでNFCが動作しない
- API 29+ のデバイスであることを確認
- NFC設定が有効になっていることを確認
- 実機でのみ動作します（エミュレータ不可）

### iOSでNFCが動作しない
- iOS 16.0+ のデバイスであることを確認
- 実機でのみ動作します（シミュレータ不可）
- Info.plistのNFC設定を確認

## 📄 ライセンス

[MIT License](LICENSE)

## 🤝 貢献

バグ報告、機能要望、プルリクエストを歓迎します！

## 📞 サポート

技術的な問題や質問があれば、Issueを開いてください。