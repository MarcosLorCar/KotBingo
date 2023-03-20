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
        hostWriter.println("${player.displayName} joined ($playerCount/15)")
        hostWriter.flush()
    }

    private fun playerDisconnect(player: BingoPlayer) {
        players.remove(player)
        hostWriter.println("${player.displayName} disconnected ($playerCount/15)")
        hostWriter.flush()
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            gameHandler()
        }
    }

    private fun gameHandler() = runBlocking {
        val cancelled = async(Dispatchers.IO) {
            when(hostReader.readLine()) {
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

        //End of game
        bingoGames.remove(this@BingoGame)
    }

    private fun winner(): BingoPlayer? {
        return null
    }
}

private fun MutableList<BingoPlayer>.println(str: String) {
    forEach {
        it.writer.println(str)
        it.writer.flush()
    }
}
