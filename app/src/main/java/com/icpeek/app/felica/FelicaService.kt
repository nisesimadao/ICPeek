package com.icpeek.app.felica

import java.io.ByteArrayOutputStream

class FelicaService {

    companion object {
        // FeliCa command codes
        const val READ_WITHOUT_ENCRYPTION = 0x06
        const val POLLING = 0x00
        
        // Service codes for balance reading
        const val SERVICE_CODE_BALANCE = 0x090F  // Common balance service (Suica/PASMO/ICOCA)
        const val SERVICE_CODE_SUICA = 0x090F   // Suica balance
        const val SERVICE_CODE_ICOCA = 0x090F  // ICOCA balance service (same as Suica)
        const val SERVICE_CODE_PASMO = 0x090F   // PASMO balance
        const val SERVICE_CODE_EDY = 0x170F     // Edy service (not ICOCA)
        
        // Block codes for balance data
        const val BLOCK_CODE_BALANCE = 0x0000
        
        // System codes for common cards
        const val SYSTEM_CODE_SUICA = 0x0003
        const val SYSTEM_CODE_PASMO = 0x0003
        const val SYSTEM_CODE_ICOCA = 0x0003  // ICOCA uses 0x0003
    }

    /**
     * Creates a polling command to get card information
     */
    fun createPollingCommand(): ByteArray {
        val command = ByteArray(6)
        
        command[0] = 0x06.toByte() // Length
        command[1] = POLLING.toByte() // Command code
        command[2] = 0xFF.toByte() // System code (wildcard)
        command[3] = 0xFF.toByte()
        command[4] = 0x01.toByte() // Request code
        command[5] = 0x0F.toByte() // Time slot
        
        return command
    }

    /**
     * Creates a Read Without Encryption command to read balance
     * Based on CardReader-master implementation
     */
    fun createReadWithoutEncryptionCommand(serviceCode: Int = SERVICE_CODE_BALANCE): ByteArray {
        val command = ByteArrayOutputStream(100)
        
        command.write(0)                    // data length. change after all byte set.
        command.write(READ_WITHOUT_ENCRYPTION)  // Felica command, Read Without Encryption
        command.write(ByteArray(8))        // NFC ID (8byte) - will be filled later
        
        command.write(1)                    // service code length (2byte)
        command.write(serviceCode and 0xFF)  // low byte of service code (little endian)
        command.write(serviceCode shr 8)     // high byte of service code (little endian)
        command.write(1)                    // number of block. (=< 15)
        
        command.write(0x80)                 // ブロックエレメント上位バイト
        command.write(0)                    // ブロック番号
        
        val result = command.toByteArray()
        result[0] = (result.size).toByte()  // 先頭１バイトはデータ長
        return result
    }

    /**
     * Creates a Read Without Encryption command with specific IDm
     */
    fun createReadWithoutEncryptionCommand(idm: ByteArray, serviceCode: Int = SERVICE_CODE_BALANCE): ByteArray {
        val command = createReadWithoutEncryptionCommand(serviceCode)
        
        // Copy IDm to command
        System.arraycopy(idm, 0, command, 2, 8)
        
        // Update length
        command[0] = (command.size - 1).toByte()
        
        return command
    }
}
