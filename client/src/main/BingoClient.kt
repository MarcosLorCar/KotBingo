package main

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.IOError
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.*
import kotlin.system.exitProcess

fun main() = BingoClient.menu()

object BingoClient {
    private var displayName: String
    private var serverReader: BufferedReader
    private var serverWriter: PrintWriter

    init {
        val ip: String
        val port: Int

        //Get server info and name from config file
        File("config.txt").run {
            if (!this.exists()) {
                this.createNewFile()
                this.writeText(
                    """
                name =
                server-ip =
                server-port =
            """.trimIndent()
                )
            }
            with(Properties().also { it.load(this.inputStream()) }) {
                displayName = this["name"]?.toString() ?: ""
                    .ifEmpty { "Player${Random().nextInt(1000, 9999)}" }
                ip = this["server-ip"]?.toString() ?: ""
                    .ifEmpty { "127.0.0.1" }
                port = this["server-port"].toString().toIntOrNull() ?: 0
            }
        }

        //Try to connect to the server
        try {
            val server = Socket(ip, port)
            serverWriter = PrintWriter(server.getOutputStream())
            serverReader = BufferedReader(InputStreamReader(server.getInputStream()))
            serverWriter.println(displayName)
            serverWriter.flush()
            Runtime.getRuntime().addShutdownHook(Thread { server.close() })
        } catch (_: Exception) {
            println("Error connecting to $ip:$port")
            exitProcess(0)
        }
    }

    private fun joinGame() {
        clearScr()

        serverWriter.println("BROWSE")
        serverWriter.flush()
        val games: MutableList<Triple<String, Int, Boolean>> =
            mutableListOf() //(NAME,PLAYER COUNT,ISPLAYING)

        games.run {
            val gameList = serverReader.readLine().split("#")
            if (gameList[0] == "EMPTY" && gameList[0].split(";").size == 1) {
                return@run
            }
            for (game in gameList) {
                if (game.isEmpty()) continue
                val name = game.split(";")[0]
                val playerCount = game.split(";")[1].toInt()
                val isPlaying = game.split(";")[2].toBoolean()
                games.add(Triple(name, playerCount, isPlaying))
            }
        }

        if (games.isEmpty()) {
            println("No games available")
            return
        }

        games.forEachIndexed { index, game ->
            println("(${index + 1}) ${game.first}'s game (${game.second}/15)${if (game.third) " [PLAYING]" else ""}")
        }
        println("(${games.size + 1}) Cancel")

        var response: Int
        do {
            response = intResponse { it in 1..games.size + 1 }
                .also { if (it == games.size + 1) {
                    println("Cancelled") //DEBUG
                    return
                } }
        } while (
            (games[response - 1].second == 15).also {
                if (it) println("That game is already full")
            }
        )

        response--

        println("Joining ${games[response].first}'s game...")
        serverWriter.println("JOIN $response")
        serverWriter.flush()

        when(serverReader.readLine()) {
            "JOINED" -> play()
            "ERROR" -> {
                println("Something went wrong...")
                return
            }
        }
    }

    private fun play() {
        TODO()
    }

    private fun hostGame(): Unit = runBlocking(Dispatchers.IO) {
        clearScr()

        serverWriter.println("HOST")
        serverWriter.flush()
        val cancelled = CoroutineScope(Dispatchers.IO).async {
            println("""
                Waiting for players...
                (1) Start game
                (2) Cancel
            """.trimIndent())
            return@async when(intResponse { it in 1..2 }) {
                1 -> {
                    serverWriter.println("START")
                    serverWriter.flush()
                    false
                }
                2 -> {
                    serverWriter.println("CANCEL")
                    serverWriter.flush()
                    true
                }
                else -> throw IOException()
            }
        }

        while (!cancelled.isCompleted) {
            if (serverReader.ready()) {
                serverReader.readLine().also { println(it) }
            }
            delay(1000)
        }

        if (cancelled.await()) {
            serverWriter.println("CANCEL")
            serverWriter.flush()
            return@runBlocking
        }

        serverWriter.println("START")
        serverWriter.flush()


        // Game ended
    }

    fun menu() {
        println(
            """
            (1) Join game
            (2) Host game
            (3) Exit
        """.trimIndent()
        )
        when (intResponse { it in 1..3 }) {
            1 -> joinGame()
            2 -> hostGame()
            3 -> exitProcess(0)
        }
        menu()
    }

    private fun intResponse(action: ((Int) -> Any)? = null, predicate: (Int) -> Boolean): Int {
        while (true) {
            val input = readln().toIntOrNull()
            if (input != null && predicate(input)) {
                action?.invoke(input) ?: return input
            } else println("Invalid response")
        }
    }
}

fun clearScr() {
    ProcessBuilder("cmd","/c","cls").inheritIO().start().waitFor()
}