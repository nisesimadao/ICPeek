package com.icpeek.shared.felica

import kotlin.ExperimentalStdlibApi
import kotlin.format

@OptIn(ExperimentalStdlibApi::class)
class FelicaService {

    companion object {
        // FeliCa command codes
        const val READ_WITHOUT_ENCRYPTION = 0x06
        const val POLLING = 0x00
        
        // Service codes for balance reading
        const val SERVICE_CODE_BALANCE = 0x090F
        const val SERVICE_CODE_SUICA = 0x090F
        const val SERVICE_CODE_ICOCA = 0x090F
        const val SERVICE_CODE_PASMO = 0x090F
        const val SERVICE_CODE_EDY = 0x170F
        
        // Block codes for balance data
        const val BLOCK_CODE_BALANCE = 0x0000
        
        // System codes for common cards
        const val SYSTEM_CODE_SUICA = 0x0003
        const val SYSTEM_CODE_PASMO = 0x0003
        const val SYSTEM_CODE_ICOCA = 0x0003
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
     */
    fun createReadWithoutEncryptionCommand(serviceCode: Int = SERVICE_CODE_BALANCE): ByteArray {
        val command = mutableListOf<Byte>()
        
        command.add(0) // data length. change after all byte set.
        command.add(READ_WITHOUT_ENCRYPTION.toByte()) // Felica command
        
        // NFC ID (8byte) - will be filled later
        repeat(8) { command.add(0) }
        
        command.add(1) // service code length (2byte)
        command.add((serviceCode shr 8).toByte()) // high byte of service code (big endian)
        command.add((serviceCode and 0xFF).toByte()) // low byte of service code (big endian)
        command.add(1) // number of block
        
        command.add(0x80.toByte()) // ブロックエレメント上位バイト
        command.add(0) // ブロック番号
        
        val result = command.toByteArray()
        result[0] = (result.size).toByte() // 先頭１バイトはデータ長
        return result
    }

    /**
     * Creates a Read Without Encryption command with specific IDm
     */
    fun createReadWithoutEncryptionCommand(idm: ByteArray, serviceCode: Int = SERVICE_CODE_BALANCE): ByteArray {
        val command = createReadWithoutEncryptionCommand(serviceCode)
        
        // Copy IDm to command
        idm.forEachIndexed { index, byte ->
            if (index < 8) {
                command[index + 2] = byte
            }
        }
        
        // Update length
        command[0] = (command.size - 1).toByte()
        
        return command
    }

    /**
     * Creates a command to read multiple history blocks
     */
    fun createHistoryReadCommand(idm: ByteArray, blockCount: Int): ByteArray {
        val command = mutableListOf<Byte>()
        
        command.add(0) // data length. change after all byte set.
        command.add(0x06.toByte()) // Felica command, Read Without Encryption
        
        // NFC ID (8byte)
        command.addAll(idm.toList())
        
        command.add(1) // service code length (2byte)
        command.add(0x0F.toByte()) // low byte of service code (little endian)
        command.add(0x09.toByte()) // high byte of service code (little endian)
        command.add(blockCount.toByte()) // number of blocks
        
        for (i in 0 until blockCount) {
            command.add(0x80.toByte()) // ブロックエレメント上位バイト
            command.add(i.toByte()) // ブロック番号
        }
        
        val result = command.toByteArray()
        result[0] = (result.size).toByte() // 先頭１バイトはデータ長
        return result
    }
}

/**
 * Extension function to convert ByteArray to hex string
 */
fun ByteArray.toHexString(): String {
    return this.joinToString("") { "%02X".format(it) }
}
