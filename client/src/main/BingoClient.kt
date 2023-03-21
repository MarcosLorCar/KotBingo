package main

import kotlinx.coroutines.*
import java.io.*
import java.net.Socket
import java.util.*
import kotlin.system.exitProcess

fun main() {
    clearScr()
    BingoClient.menu()
}

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
                name = Player
                server-ip = 0.0.0.0
                server-port = 0
            """.trimIndent()
                )
            }
            with(Properties().also { it.load(this.inputStream()) }) {
                displayName = this["name"]?.toString() ?: ""
                    .ifEmpty { "Player${Random().nextInt(1000, 9999)}" }
                ip = this["server-ip"]?.toString() ?: ""
                    .ifEmpty { "0.0.0.0" }
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
                .also {
                    if (it == games.size + 1) {
                        println("Cancelled") //DEBUG
                        return
                    }
                }
        } while (
            (games[response - 1].second == 15).also {
                if (it) println("That game is already full")
            }
            ||
            games[response - 1].third.also {
                if (it) println("That game is running")
            }
        )

        response--

        println("Joining ${games[response].first}'s game...")
        serverWriter.println("JOIN $response")
        serverWriter.flush()

        when (serverReader.readLine()) {
            "JOINED" -> play()
            "ERROR" -> {
                println("Something went wrong...")
                return
            }
        }
    }

    private fun play() = runBlocking(Dispatchers.IO) {
        clearScr()
        println("Joined the game")

        val str = async(Dispatchers.IO) {
            var str: String
            while (true) {
                str = serverReader.readLine()
                if (str.substringBefore(" ") == "PLAYER")
                    println(str.substringAfter(" "))
                else
                    break
            }
            return@async str
        }

        var cardStr: String

        if (str.await().also { cardStr = it.substringAfter(" ") } == "CANCEL") {
            println("Game cancelled")
            return@runBlocking
        }
        //Game start

        val bingoCard = cardFromString(cardStr)

        clearScr()
        println("Game started")
        printBingoCard(bingoCard)


        while (true) {
            val serverStr = serverReader.readLine()
            if (serverStr == null) {
                println("Something went wrong...")
                break
            }
            when (serverStr.substringBefore(" ")) {
                "NUMBER" -> {
                    clearScr()
                    val num = serverStr.substringAfter(" ").toInt()
                    println(
                        """
                        ┌────┐
                        │ ${(if (num < 10) "0" else "") + num} │
                        └────┘
                        """.trimIndent()
                    )
                    val prevLines = hasLine(bingoCard)
                    for ((index, i) in bingoCard.withIndex()) {
                        if (i == num) bingoCard[index] = -1
                    }
                    printBingoCard(bingoCard)
                    if (hasBingo(bingoCard)) {
                        println("You got bingo!")
                        break
                    } else if (hasLine(bingoCard) != prevLines) {
                        println("You got line!")
                    }
                }

                "LINE" -> {
                    val name = serverStr.substringAfter(" ").split(";")[0]
                    val count = serverStr.substringAfter(" ").split(";")[1]
                    println("$name  got line! ($count/3)")
                }

                "BINGO" -> {
                    val name = serverStr.substringAfter(" ")
                    println("$name  got bingo!")
                    break
                }

                "END" -> {
                    println("Game terminated by host")
                    break
                }
            }
        }

        println("Game ended. Returning in 5s")

        launch {
            delay(5000)
        }.also { it.join() }
        clearScr()
        // Game end
    }

    private fun printBingoCard(bingoCard: List<Int>) {
        val formattedNumbers = bingoCard.map {
            if (it == -1) {
                "XX"
            } else if (it < 10) {
                "0$it"
            } else {
                it.toString()
            }
        }
        println(
            """
        ┌────┬────┬────┬────┬────┐
        │ ${formattedNumbers[0]} │ ${formattedNumbers[1]} │ ${formattedNumbers[2]} │ ${formattedNumbers[3]} │ ${formattedNumbers[4]} │
        ├────┼────┼────┼────┼────┤
        │ ${formattedNumbers[5]} │ ${formattedNumbers[6]} │ ${formattedNumbers[7]} │ ${formattedNumbers[8]} │ ${formattedNumbers[9]} │
        ├────┼────┼────┼────┼────┤
        │ ${formattedNumbers[10]} │ ${formattedNumbers[11]} │ ${formattedNumbers[12]} │ ${formattedNumbers[13]} │ ${formattedNumbers[14]} │
        └────┴────┴────┴────┴────┘
    """.trimIndent()
        )
    }

    private fun hasLine(bingoCard: List<Int>): Int {
        var lines = 0
        bingoCard.toList().chunked(5).forEach { line ->
            if (line.all { it == -1 })
                lines++
        }
        return lines
    }

    private fun hasBingo(bingoCard: List<Int>): Boolean = bingoCard.all { it == -1 }


    private fun cardFromString(cardStr: String): MutableList<Int> {
        return cardStr.split(";").map { it.toInt() } as MutableList<Int>
    }

    private fun hostGame(): Unit = runBlocking(Dispatchers.IO) {
        clearScr()

        var playerCount = 0

        serverWriter.println("HOST")
        serverWriter.flush()
        val cancelled = CoroutineScope(Dispatchers.IO).async {
            println(
                """
                Waiting for players...
                (1) Start game
                (2) Cancel
            """.trimIndent()
            )
            var response: Int
            do {
                response = intResponse { it in 1..2 }
                if (response == 2) return@async true
            } while ((playerCount < 2).also { if (it) println("Cant start game with less than 2 players") })
            return@async false
        }

        while (!cancelled.isCompleted) {
            if (serverReader.ready()) {
                serverReader.readLine().also {
                    if (it.substringBefore(" ") == "PLAYER") {
                        println(it.substringAfter(" "))
                        playerCount++
                    }
                }
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

        clearScr()
        game@ while (true) {
            println(
                """
                (1) New number
                (2) Player list
                (3) End game
            """.trimIndent()
            )
            when (intResponse { it in 1..3 }) {
                1 -> {
                    clearScr()
                    serverWriter.println("NUMBER")
                    serverWriter.flush()
                    val num = serverReader.readLine().toInt()
                    println(
                        """
                        ┌────┐
                        │ ${(if (num < 10) "0" else "") + num} │
                        └────┘
                        """.trimIndent()
                    )
                    while (true) {
                        val response = serverReader.readLine()
                        when (response.substringBefore(" ")) {
                            "LINE" -> {
                                val name = response.substringAfter(" ").split(";")[0]
                                val count = response.substringAfter(" ").split(";")[1]
                                println("$name got a line! ($count/3)")
                            }

                            "BINGO" -> {
                                val name = response.substringAfter(" ")
                                println("$name got bingo!")
                                break@game
                            }

                            "NEXT" -> break
                        }
                    }
                }

                2 -> {
                    serverWriter.println("PLAYERS")
                    serverWriter.flush()
                    while (true) {
                        val response = serverReader.readLine()
                        when (response.substringBefore(" ")) {
                            "PLAYER" -> {
                                val name = response.substringAfter(" ").split(";")[0]
                                val count = response.substringAfter(" ").split(";")[1]
                                println("- $name with $count/3 lines")
                            }

                            "NEXT" -> break
                        }
                    }
                }

                3 -> {
                    serverWriter.println("END")
                    serverWriter.flush()
                    clearScr()
                    break
                }
            }
        }


        println("Game ended. Returning in 5s")

        launch {
            delay(5000)
        }.also { it.join() }
        clearScr()
        // Game end
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
    ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor()
}