package com.machinerychorus.nuggetcounter

import io.kweb.Kweb
import io.kweb.NotFoundException
import io.kweb.ROOT_PATH
import io.kweb.dom.BodyElement
import io.kweb.dom.element.creation.ElementCreator
import io.kweb.dom.element.creation.tags.*
import io.kweb.dom.element.creation.tags.InputType.text
import io.kweb.dom.element.events.on
import io.kweb.dom.element.new
import io.kweb.plugins.semanticUI.semanticUIPlugin
import io.kweb.routing.get
import io.kweb.routing.path
import io.kweb.routing.simpleUrlParser
import io.kweb.routing.url
import io.kweb.state.KVar
import io.kweb.state.persistent.render
import io.kweb.state.persistent.toVar
import io.mola.galimatias.URL
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.util.*
import io.kweb.plugins.semanticUI.semantic as s

private val logger = KotlinLogging.logger {}
const val teamUrl = "team"
const val loginUrl = "login"
const val userUrl = "user"
const val uidKey = "uid"

fun main(args: Array<String>) {
    val plugins = listOf(semanticUIPlugin, jsCookiePlugin)

    Kweb(port = 8080, debug = true, plugins = plugins) {
        doc.body.new {
            /** Kweb allows you to modularize your code however suits your needs
            best.  Here I use an extension function defined elsewhere to
            draw some common outer page DOM elements */
            pageBorderAndTitle("Nugget Counter") {

                /** A KVar is similar to an AtomicReference in the standard Java
                Library, but which supports the observer pattern and `map`
                semantics.  Here I set it to the current URL of the page.

                This will update automatically if the page's URL changes, and
                if it is modified, the page's URL will change and the DOM will
                re-render _without_ a page reload.  Yes, seriously. */
                val url: KVar<URL> = doc.receiver.url(simpleUrlParser)

                /** s.content uses the semanticUIPlugin to use the excellent
                Semantic UI framework, included as a plugin above, and implemented
                as a convenient DSL within Kweb */
                div(s.content).new {

                    /** Note how url.path[0] is itself a KVar.  Changes to firstPathElement
                    will automatically propagate _bi-directionally_ with `url`.  This
                    comes in very handy later. */
                    val firstPathElement: KVar<String> = url.path[0]

                    /** Renders `firstPathElement`, but - and here's the fun part - will
                    automatically re-render if firstPathElement changes.  This is
                    a simple, elegant, and yet powerful routing mechanism. */
                    render(firstPathElement) { entityType ->
                        when (entityType) {
                            ROOT_PATH -> {
                                div(s.ui.action.input).new {
                                    inputWithButton("Enter Team Name", "View"){ input ->
                                        openTeam(input, url)
                                    }
                                }
                            }
                            teamUrl -> {
                                render(url.path[1]) { teamName ->
                                    try {
                                        //create bucket if non-existent
                                        State.teams[teamName] = State.teams[teamName] ?: State.Team(teamName)

                                        renderTeam(toVar(State.teams, teamName))
                                    } catch (e: NoSuchElementException) {
                                        throw NotFoundException("Can't find team with name '$teamName'")
                                    }
                                    render(url.path[2]) { action ->
                                        when(action){
                                            loginUrl -> {
                                                GlobalScope.launch {
                                                    //if UID exists and matches a user, redirect to that user
                                                    val uid: String? = doc.body.jsCookie().get(uidKey).await()
                                                    if(uid != null && uid.isNotEmpty() && State.users[uid] != null) {
                                                        val user = State.users[uid]
                                                        url.path.value = listOf(teamUrl, teamName, userUrl, user!!.name)
                                                    } else {
                                                        //otherwise show login
                                                        div().new {
                                                            h4().text("Join this team:")
                                                            inputWithButton("Username", "Join!") {
                                                                val newUid = generateNewUid()
                                                                it.jsCookie().set(uidKey, newUid)
                                                                GlobalScope.launch {
                                                                    val newUsername = it.getValue().await()
                                                                    State.users[newUid] = State.User(newUid, teamName, newUsername)
                                                                    url.path.value = listOf(teamUrl, teamName, userUrl, newUsername)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            userUrl -> {
                                                GlobalScope.launch{
                                                    val uid: String? = doc.body.jsCookie().get(uidKey).await()
                                                    //if UID doesn't exist or doesn't match this user, redirect to login
                                                    if(uid == null
                                                        || uid.isEmpty()
                                                        || State.users[uid] == null
                                                        || url.path.value.size < 4
                                                        || url.path[3].value != State.users[uid]!!.name) {
                                                        url.path.value = listOf(teamUrl, teamName, loginUrl)
                                                    } else {
                                                        //otherwise show controls
                                                        h4().text(toVar(State.users, uid).map{
                                                            "You have eaten ${it.nuggetCount} nuggets"
                                                        })
                                                        div(s.ui.action.input).new {
                                                            button(s.ui.button).text("NOM").on.click{
                                                                State.users.modify(uid){ user ->
                                                                    val newCount = user.nuggetCount.inc()
                                                                    State.User(user.uid, user.teamUid, user.name, newCount)
                                                                }
                                                            }
                                                            button(s.ui.button).text("undo").on.click{
                                                                State.users.modify(uid){ user ->
                                                                    logger.debug("count before: ${user.nuggetCount}")
                                                                    var newCount = user.nuggetCount.dec()
                                                                    logger.debug("new count: ${newCount}")
                                                                    if(newCount < 0){
                                                                        newCount = 0
                                                                    }
                                                                    State.User(user.uid, user.teamUid, user.name, newCount)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                throw NotFoundException("Unrecognized entity type '$entityType', path: ${url.path.value}")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ElementCreator<BodyElement>.pageBorderAndTitle(title: String, content: ElementCreator<DivElement>.() -> Unit) {
    div(s.ui.one.column.centered.grid).new {
        div(s.column).new {
            h1(s.ui.dividing.header).text(title)
            content(this)
        }
    }
}

private fun openTeam(nameInput:InputElement, url: KVar<URL>) {
    GlobalScope.launch {
        val name = nameInput.getValue().await()
        url.path.value = listOf(teamUrl, name, loginUrl)
    }
}

private fun ElementCreator<*>.renderTeam(team: KVar<State.Team>) {
    //val users = State.usersByTeam(team.value.uid)
    h3().new{
        div().text(team.map{ "Team ${it.uid} has ${it.nuggetsRemaining} nuggets remaining."})
    }
    //TODO: show list of users and their count
}

/**
 * Creates an input element with a corresponding button. Pressing "Enter" from inside the input
 * and clicking the add button will trigger the same action. Input element is passed to the action.
 */
private fun ElementCreator<DivElement>.inputWithButton(placeholder:String, buttonLabel:String, action: (input:InputElement) -> Unit){
    div(s.ui.action.input).new {
        val input = input(text, placeholder = placeholder).apply {
            on.keypress { ke ->
                if (ke.code == "Enter") {
                    action(this)
                }
            }
        }
        button(s.ui.button).text(buttonLabel).apply {
            on.click {
                action(input)
            }
        }
    }
}

private fun generateNewUid() = UUID.randomUUID().toString()