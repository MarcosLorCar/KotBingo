package main

import java.io.BufferedReader
import java.io.PrintWriter
import java.net.Socket

data class BingoPlayer(
    var bingoCard: Array<Int>,
    val writer: PrintWriter,
    val reader: BufferedReader,
    val displayName: String,
    val socket: Socket
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BingoPlayer

        if (!bingoCard.contentEquals(other.bingoCard)) return false
        if (writer != other.writer) return false
        if (reader != other.reader) return false
        if (displayName != other.displayName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bingoCard.contentHashCode()
        result = 31 * result + writer.hashCode()
        result = 31 * result + reader.hashCode()
        result = 31 * result + displayName.hashCode()
        return result
    }

    fun hasLine(): Boolean {
        bingoCard.toList().chunked(5).forEach { line ->
            if (line.all { it == -1 })
                return true
        }
        return false
    }

    fun hasBingo(): Boolean = bingoCard.all { it == -1 }
    fun println(s: String) {
        writer.println(s)
        writer.flush()
    }
}