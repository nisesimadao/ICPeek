# ICPeek - IC Card Balance Viewer (Android) - Design Document

## Goal
Android NFCを使用してFeliCa系交通ICカードの残高を読み取るシンプルなアプリを作る。

対象カード例:
- Suica
- ICOCA
- PASMO

## Scope
最初のバージョンでは以下のみ実装する。
- NFC検出
- FeliCa通信
- 残高取得
- 画面表示

履歴解析などは将来拡張。

## Tech Stack
- Android
- Java または Kotlin
- Android NFC API

## Architecture

Activity
└ NFC Handler
    └ FeliCa Reader
        └ Balance Parser


## NFC Detection

AndroidのTECH_DISCOVEREDを使用。

NFC Tech:
- android.nfc.tech.NfcF


## FeliCa Communication

Service Code:
0x090F

Read Without Encryption コマンドを使用。


## Data Format

Response structure:

[0] length
[1] response code
[2..9] IDm
...

Balance:

byte[10]
byte[11]

balance = (byte11 << 8) | byte10


## UI

Main screen:

TextView

Example:

ICOCA

Balance: ¥1230


## Future Extensions

- 取引履歴
- カード種別検出
- 複数カード対応
- ウィジェット

## Repository Structure

/app
  MainActivity
  NFCReader
  FelicaService
  BalanceParser

/res
  layout
  xml


## Development Steps

1. Androidプロジェクトを作成する
2. NFC権限を有効にする
3. NFCタグを検出する
4. FeliCaコマンドを送信する
5. レスポンスを解析する
6. 残高を表示する

## License

MIT

