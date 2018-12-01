package com.machinerychorus.nuggetcounter

import io.kweb.shoebox.Shoebox
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

object State {
    private val logger = KotlinLogging.logger {}
    val dir: Path = Files.createDirectories(Paths.get("", "shoebox"))

    /**
     * nuggetCount is stored as a string rather than an int because
     * Element.text() only accepts KVal<String>
     */
    data class Team(val uid: String, val nuggetsRemaining: String = "2000")

    data class User(val uid: String, val teamUid: String, val name:String, val nuggetCount: String = "0")

    val teams = Shoebox<Team>(dir.resolve("teams"))

    val users = Shoebox<User>(dir.resolve("users"))

    fun usersByTeam(teamUid: String) = users.view("usersByTeam", User::teamUid).orderedSet(teamUid, compareBy(
        User::name))

    init {
        //wire the listeners for keeping the count up-to-date
        users.onChange{ prevVal, newVal, src ->
            logger.debug("User was changed.")
            //remove the previous value from the nugget count
            teams.modify(prevVal.teamUid){
                val nuggets = prevVal.nuggetCount.toInt()
                Team(it.uid, it.nuggetsRemaining.toInt().plus(nuggets).toString())
            }

            //add the new value
            val newUser = newVal.value
            teams.modify(newUser.teamUid){
                val nuggets = newUser.nuggetCount.toInt()
                Team(it.uid, it.nuggetsRemaining.toInt().minus(nuggets).toString())
            }
            //I think this will also cover people switching teams
        }
    }

}