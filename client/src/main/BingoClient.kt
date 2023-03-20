package main.kotlin

import java.io.BufferedReader
import java.io.PrintWriter

class BingoClient(val displayName: String, val serverReader: BufferedReader, val serverWriter: PrintWriter) {

    fun joinGame() {
        serverWriter.println("BROWSE")
        serverWriter.flush()
        val games = mutableListOf<Pair<String, Int>>() //(NAME,PLAYER COUNT)
            .also {
                val gameList = serverReader.readLines()
                for (game in gameList) {
                    val name = game.split(";")[0]
                    val playerCount = game.split(";")[1].toInt()
                    it.add(Pair(name, playerCount))
                }
            }
        games.forEachIndexed { index, pair ->
            println("(${index + 1}) ${pair.first}'s game (${pair.second}/15)")
        }
        println("(${games.size + 1}) Cancel")
        val response = fun(): Int {
            while (true) {
                val input = prompt("-> ").toString().toIntOrNull()
                if (input != null && input > 0 && input <= games.size + 1 ) {
                    if (games[input-1].second>=15)
                        println("That game is already full")
                    else
                        return input
                } else println("Invalid option")
            }
        }()
        if (response == games.size+1)
            return
        serverWriter.println("JOIN $response")
        play()
    }

    private fun play() {
        TODO("Not yet implemented")
    }

    fun hostGame() {
        TODO("Not yet implemented")
    }
}