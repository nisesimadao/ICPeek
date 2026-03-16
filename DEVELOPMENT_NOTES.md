# ICPeek KMP 開発メモ

## 移行の要点

### 1. API 29+ 専用化
- ReaderMode APIのみ使用（API 28以下は非対応）
- 最小SDKを29に引き上げ
- NFC Intent handlingを削除

### 2. 共有モジュール化
- FelicaService: FeliCaコマンド生成
- BalanceParser: 残高・取引解析
- TransactionInfo: データモデル共通化
- NFCReader: プラットフォーム抽象化

### 3. iOS実装
- SwiftUIでモダンUIを構築
- CoreNFCでiOS NFC機能を実装
- KotlinコードをFrameworkとしてリンク

## 技術的決定事項

### Kotlin Multiplatform構成
```
shared/
├── commonMain/kotlin/
│   ├── felica/FelicaService.kt
│   ├── nfc/NFCReader.kt (expect/actual)
│   ├── parser/BalanceParser.kt
│   └── model/TransactionInfo.kt
├── androidMain/kotlin/
│   └── platform/NFCReader.android.kt
└── iosMain/kotlin/
    └── platform/NFCReader.ios.kt
```

### データフロー
```
NFC Tag → Platform NFCReader → Shared NFCReader → FelicaService → BalanceParser → TransactionInfo
```

### Android側変更点
- MainActivityをAPI 29+専用に書き直し
- ReaderCallback実装
- 共有モジュール依存追加
- TransactionInfoをshared.modelに変更

### iOS側実装
- SwiftUI + MVVMアーキテクチャ
- CoreNFCのNFCTagReaderSession使用
- Kotlin/NativeのsuspendCancellableCoroutineで連携

## 既知の制約

### Java 25 互換性
- Gradle 8.11.1 + Kotlin 2.0.0 で対応
- `--enable-native-access=ALL-UNNAMED` が必要
- ビルド時に警告が出るが動作は正常

### iOS制約
- CoreNFCは実機のみで動作
- iOS 16.0+ 必須
- SimulatorではNFCテスト不可

### Android制約
- API 29+ のみサポート
- ReaderMode専用なので旧API非対応
- NFC権限が必要

## テスト戦略

### 単体テスト
- BalanceParserのロジックテスト
- FelicaServiceのコマンド生成テスト
- TransactionInfoのデータ変換テスト

### 統合テスト
- Android実機でのNFC読み取りテスト
- iOS実機でのNFC読み取りテスト
- 各カード種別での動作確認

### UIテスト
- Android Espressoテスト
- iOS XCTestテスト

## デプロイ準備

### Android
- minSdk 29, targetSdk 34
- ProGuard設定（リリース時）
- Signing設定

### iOS
- iOS 16.0+ ターゲット
- App Store Signing
- Info.plist設定

## パフォーマンス考慮

### NFC読み取り速度
- ReaderModeで即時検出
- コマンド間に適切なdelayを設定
- エラーハンドリングで即時リトライ

### メモリ使用量
- 共有モジュールでメモリ効率化
- 取引履歴は最大10件に制限
- 画像リソースの最適化

## セキュリティ

### NFC通信
- FeliCaの暗号化通信を利用
- 生データへのアクセスは最小限
- エラーログに機密情報を含めない

### データ保存
- ローカル保存は最小限
- ネットワーク通信なし
- ユーザーデータの収集なし

## 今後の改善点

### 機能拡張
- リアルタイム残高更新
- 複数カード管理
- 取引履歴の検索・フィルター
- CSVエクスポート

### 技術改善
- Web版実装 (Kotlin/Wasm)
- Desktop版実装 (Compose Desktop)
- クラウド同期機能
- プッシュ通知

### UI/UX改善
- アニメーション追加
- ダークモード対応
- アクセシビリティ改善
- 多言語対応拡充

## 保守性

### コード品質
- Kotlinコーディング規約準拠
- 適切なコメントとドキュメント
- 一貫性のある命名規則

### ビルドシステム
- Gradle Wrapper使用
- 依存関係の明確化
- CI/CDパイプライン準備

### バージョン管理
- セマンティックバージョニング
- リリースノート作成
- タグ付け戦略
