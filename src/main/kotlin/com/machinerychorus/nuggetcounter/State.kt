package com.machinerychorus.nuggetcounter

import io.kweb.shoebox.Shoebox
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object State {
    private val logger = KotlinLogging.logger {}
    val dir: Path = Files.createDirectories(Paths.get("", "shoebox"))

    data class Team(val uid: String, val nuggetsRemaining: Int = 2000)

    data class User(val uid: String, val teamUid: String, val name:String, val nuggetCount: Int = 0)

    val teams = Shoebox<Team>(dir.resolve("teams"))

    val users = Shoebox<User>(dir.resolve("users"))

    //You can only have one instance of each view (or shoebox) because of the way locking works in directorystore
    private val usersByTeam = users.view("usersByTeam", User::teamUid)

    fun usersByTeam(teamUid: String) = usersByTeam.orderedSet(teamUid, compareBy(User::name))

    init {
        //wire the listeners for keeping the count up-to-date
        users.onChange{ prevVal, newVal, _ ->
            logger.debug("User was changed. ${prevVal.nuggetCount} -> ${newVal.value.nuggetCount}")
            //remove the previous value from the nugget count
            teams.modify(prevVal.teamUid){
                Team(it.uid, it.nuggetsRemaining.plus(prevVal.nuggetCount))
            }

            //add the new value
            val newUser = newVal.value
            teams.modify(newUser.teamUid){
                Team(it.uid, it.nuggetsRemaining.minus(newUser.nuggetCount))
            }
            //I think this will also cover people switching teams
        }
    }

}