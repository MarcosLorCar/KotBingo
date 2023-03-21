package main

import kotlinx.coroutines.*

class BingoGame(val host: BingoPlayer) {

    private val hostWriter = host.writer
    private val hostReader = host.reader

    val playerCount: Int
        get() = players.size

    var playing: Boolean = false

    private val players: MutableList<BingoPlayer> = mutableListOf()

    fun playerJoin(player: BingoPlayer) {
        players.add(player)
        hostWriter.println("PLAYER ${player.displayName} joined ($playerCount/15)")
        players.println("PLAYER ${player.displayName} joined ($playerCount/15)")
        hostWriter.flush()
    }

    private fun playerDisconnect(player: BingoPlayer) {
        players.remove(player)
        hostWriter.println("PLAYER ${player.displayName} disconnected ($playerCount/15)")
        players.println("PLAYER ${player.displayName} disconnected ($playerCount/15)")
        hostWriter.flush()
    }

    fun gameHandle() = runBlocking(Dispatchers.IO) {
        val cancelled = async(Dispatchers.IO) {
            when (hostReader.readLine()) {
                "START" -> false
                "CANCEL" -> true
                else -> true
            }
        }

        launch(Dispatchers.IO) { //Disconnect listener
            while (!cancelled.isCompleted) {
                players.forEach { if (it.socket.isClosed) playerDisconnect(it) }
                delay(300)
            }
        }

        if (cancelled.await()) { // Play game
            bingoGames.remove(this@BingoGame)
            players.println("CANCEL")
            return@runBlocking
        }
        playing = true
        for (player in players) {
            player.println("START ${stringFromCard(player.bingoCard)}")
        }

        val numbers = mutableListOf<Int>()

        for (i in 1..99) {
            numbers.add(i)
        }

        // Game start
        val lines: MutableMap<BingoPlayer, Int> = players.associateWith { 0 } as MutableMap<BingoPlayer, Int>
        while (true) {
            when (hostReader.readLine()) {
                "NUMBER" -> {
                    val newNumber = numbers.random()
                    numbers.remove(newNumber)

                    hostWriter.println(newNumber)
                    hostWriter.flush()

                    players.forEach {
                        for ((index, i) in it.bingoCard.withIndex()) {
                            if (i == newNumber) it.bingoCard[index] = -1
                        }
                    }

                    players.println("NUMBER $newNumber")
                    players.forEach { player ->
                        if (lines.any { it.value == 3 }) return@forEach
                        if (player.hasBingo()) {
                            lines[player] = lines[player]!! + 1
                            players.println("BINGO ${player.displayName}")
                            hostWriter.println("BINGO ${player.displayName}")
                            hostWriter.flush()
                        } else if (player.hasLine() > lines[player]!!) {
                            lines[player] = lines[player]!! + 1
                            players.println("LINE ${player.displayName};${lines[player]}")
                            hostWriter.println("LINE ${player.displayName};${lines[player]}")
                            hostWriter.flush()
                        }
                    }
                    if (lines.any { it.value == 3 })
                        break
                    hostWriter.println("NEXT")
                    hostWriter.flush()
                }

                "PLAYERS" -> {
                    players.forEach {
                        hostWriter.println("PLAYER ${it.displayName};${lines[it]}")
                        hostWriter.flush()
                    }
                    hostWriter.println("NEXT")
                    hostWriter.flush()
                }

                "END" -> {
                    players.println("END")
                    break
                }
            }
        }

        playing = false
        // Game end
        bingoGames.remove(this@BingoGame)
    }

    private fun stringFromCard(bingoCard: List<Int>): String {
        return bingoCard.joinToString(";") { it.toString() }
    }
}

private fun MutableList<BingoPlayer>.println(str: String) {
    forEach {
        it.writer.println(str)
        it.writer.flush()
    }
}
