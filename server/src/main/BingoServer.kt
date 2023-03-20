package main

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.random.Random
import kotlin.system.exitProcess

val bingoGames: MutableList<BingoGame> = mutableListOf()


fun main(args: Array<String>) = runBlocking(Dispatchers.IO) {
    if (args.isEmpty() || args[0].toIntOrNull() == null) throw IllegalArgumentException("Invalid port argument")
    val port = args[0].toInt()
    val serverSocket: ServerSocket
    try {
        serverSocket = ServerSocket(port)
        Runtime.getRuntime().addShutdownHook(
            Thread { serverSocket.close() }
        )
    } catch (_: Exception) {
        println("Error binding to $port")
        exitProcess(0)
    }

    clearScr()
    println("Listening for connections")
    while (true) {
        serverSocket.accept().also {
            launch {
                handleClient(it)
            }
        }
    }
}

fun handleClient(socket: Socket) {
    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    val writer = PrintWriter(socket.getOutputStream())
    val player = BingoPlayer(
        newBingoCard(),
        writer,
        reader,
        reader.readLine(),
        socket
    )
    println("${player.displayName} (${socket.remoteSocketAddress}) connected")
    while (true) {
        val req = reader.readLine()
        if (req == null) {
            socket.close()
            return
        }
        println("${player.displayName} (${socket.remoteSocketAddress}) issued:\n\t$req")
        when (req.split(" ")[0]) {
            "BROWSE" -> {
                if (bingoGames.isEmpty()) {
                    writer.println("EMPTY")
                    writer.flush()
                } else {
                    writer.println(gameList())
                    writer.flush()
                }
            }

            "JOIN" -> {
                val gameIndex = req.split(" ")[1].toInt()
                if (gameIndex >= bingoGames.size || bingoGames[gameIndex].playing || bingoGames[gameIndex].playerCount == 15) {
                    writer.println("ERROR")
                    writer.flush()
                } else {
                    writer.println("JOINED")
                    writer.flush()
                    bingoGames[gameIndex].playerJoin(player)
                }
            }
            "HOST" -> {
                bingoGames.add(BingoGame(player))
            }
        }
    }
}

fun clearScr() {
    ProcessBuilder("cmd","/c","cls").inheritIO().start().waitFor()
}

fun gameList(): String {
    if (bingoGames.isEmpty()) return "EMPTY"
    bingoGames.map {
        "${it.host.displayName};${it.playerCount};${it.playing}#"
    }.also { return it.joinToString() }
}

fun newBingoCard(): Array<Array<Int>> {
    val numbers = mutableListOf<Int>()
    repeat(15) {
        var num = Random.nextInt(1, 99)
        while (numbers.contains(num)) {
            num = Random.nextInt(1, 99)
        }
        numbers.add(num)
    }
    return arrayOf(
        numbers.subList(0, 4).toTypedArray(),
        numbers.subList(5, 9).toTypedArray(),
        numbers.subList(10, 14).toTypedArray()
    )
}
