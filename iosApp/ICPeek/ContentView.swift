import SwiftUI
import Shared

struct ContentView: View {
    @StateObject private var nfcReader = NFCReaderViewModel()
    @State private var showingAlert = false
    @State private var alertMessage = ""
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                // Header
                VStack(spacing: 8) {
                    Text("ICPeek")
                        .font(.largeTitle)
                        .fontWeight(.bold)
                        .foregroundColor(.blue)
                    
                    Text("ICカード残高読み取り")
                        .font(.subheadline)
                        .foregroundColor(.gray)
                }
                .padding(.top)
                
                // Card Info Display
                if let cardInfo = nfcReader.cardInfo {
                    CardInfoView(cardInfo: cardInfo)
                } else {
                    EmptyCardView()
                }
                
                // Read Button
                Button(action: {
                    readCard()
                }) {
                    HStack {
                        Image(systemName: "antenna.radiowaves.left.and.right")
                            .font(.title2)
                        Text("カード読み取り")
                            .font(.headline)
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.blue)
                    .cornerRadius(12)
                }
                .disabled(nfcReader.isReading)
                
                // Status
                Text(nfcReader.status)
                    .font(.caption)
                    .foregroundColor(.gray)
                    .padding(.horizontal)
                
                Spacer()
            }
            .padding()
            .navigationTitle("ICPeek")
            .navigationBarHidden(true)
            .alert("エラー", isPresented: $showingAlert) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(alertMessage)
            }
        }
    }
    
    private func readCard() {
        Task {
            do {
                try await nfcReader.readCard()
            } catch {
                alertMessage = error.localizedDescription
                showingAlert = true
            }
        }
    }
}

struct CardInfoView: View {
    let cardInfo: CardInfo
    
    var body: some View {
        VStack(spacing: 16) {
            // Card Type
            HStack {
                Image(systemName: "creditcard")
                    .foregroundColor(cardTypeColor)
                Text(cardInfo.cardType)
                    .font(.title2)
                    .fontWeight(.semibold)
                Spacer()
            }
            
            // Balance
            HStack {
                Text("残高")
                    .foregroundColor(.gray)
                Spacer()
                Text("¥\(cardInfo.balance)")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(.green)
            }
            .padding(.vertical, 8)
            
            // Transactions
            if !cardInfo.transactions.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("取引履歴")
                        .font(.headline)
                        .foregroundColor(.gray)
                    
                    ForEach(Array(cardInfo.transactions.prefix(5).enumerated()), id: \.offset) { index, transaction in
                        TransactionRowView(transaction: transaction)
                    }
                    
                    if cardInfo.transactions.count > 5 {
                        Text("他 \(cardInfo.transactions.count - 5) 件...")
                            .font(.caption)
                            .foregroundColor(.gray)
                            .padding(.top, 4)
                    }
                }
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(16)
        .padding(.horizontal)
    }
    
    private var cardTypeColor: Color {
        switch cardInfo.cardType {
        case let type where type.contains("Suica") || type.contains("PASMO"):
            return .green
        case let type where type.contains("ICOCA"):
            return .blue
        case let type where type.contains("Edy"):
            return .orange
        case let type where type.contains("WAON"):
            return .pink
        case let type where type.contains("nanaco"):
            return .purple
        default:
            return .gray
        }
    }
}

struct EmptyCardView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "creditcard")
                .font(.system(size: 60))
                .foregroundColor(.gray)
            
            Text("ICカードをかざしてください")
                .font(.headline)
                .foregroundColor(.gray)
            
            Text("対応カード: Suica, ICOCA, PASMO, Edy, WAON, nanaco")
                .font(.caption)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
        }
        .padding()
        .frame(height: 200)
        .background(Color(.systemGray6))
        .cornerRadius(16)
        .padding(.horizontal)
    }
}

struct TransactionRowView: View {
    let transaction: TransactionInfo
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(transaction.getFormattedDate())
                    .font(.caption)
                    .foregroundColor(.gray)
                
                Text(transaction.processName)
                    .font(.subheadline)
                    .fontWeight(.medium)
            }
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 2) {
                Text(transaction.getFormattedBalance())
                    .font(.subheadline)
                    .fontWeight(.semibold)
                
                Text(transaction.getFormattedAmountChange())
                    .font(.caption)
                    .foregroundColor(amountChangeColor)
            }
        }
        .padding(.vertical, 4)
    }
    
    private var amountChangeColor: Color {
        let change = transaction.getAmountChange()
        if change > 0 {
            return .green
        } else if change < 0 {
            return .red
        } else {
            return .gray
        }
    }
}

// NFC Reader ViewModel
@MainActor
class NFCReaderViewModel: ObservableObject {
    @Published var cardInfo: CardInfo?
    @Published var isReading = false
    @Published var status = "準備完了"
    
    private let nfcReader = NFCReader()
    
    func readCard() async throws {
        isReading = true
        status = "カード読み取り中..."
        
        do {
            cardInfo = try await nfcReader.readCard()
            status = "読み取り完了"
        } catch {
            status = "読み取りエラー"
            throw error
        }
        
        isReading = false
    }
}

#Preview {
    ContentView()
}
