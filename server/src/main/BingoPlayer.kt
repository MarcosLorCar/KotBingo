package main

import java.io.BufferedReader
import java.io.PrintWriter
import java.net.Socket

class BingoPlayer(
    var bingoCard: MutableList<Int>,
    val writer: PrintWriter,
    val reader: BufferedReader,
    val displayName: String,
    val socket: Socket
) {

    fun hasLine(): Int {
        var lines = 0
        bingoCard.chunked(5).forEach { line ->
            if (line.all { it == -1 })
                lines++
        }
        return lines
    }

    fun hasBingo(): Boolean = bingoCard.all { it == -1 }
    fun println(s: String) {
        writer.println(s)
        writer.flush()
    }
}