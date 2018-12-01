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
                                    render(url.path[2]) { userName ->
                                        GlobalScope.launch {
                                            val uid: String? = doc.body.jsCookie().get(uidKey).await()

                                            //if uid doesn't exist, show login
                                            if(uid == null || uid.isEmpty()){
                                                div().new {
                                                    h4().text("Join this team:")
                                                    inputWithButton("Username", "Join!") {
                                                        val newUid = generateNewUid()
                                                        it.jsCookie().set(uidKey, newUid)
                                                        GlobalScope.launch {
                                                            val newUsername = it.getValue().await()
                                                            State.users[newUid] = State.User(newUid, teamName, newUsername)
                                                            url.path.value = listOf(teamUrl, teamName, newUsername)
                                                        }
                                                    }
                                                }
                                            } else {
                                                val userByUid = State.users[uid]
                                                if(userByUid == null){
                                                    GlobalScope.launch{
                                                        doc.body.jsCookie().remove(uidKey)
                                                        url.path.value = listOf(teamUrl, teamName)
                                                    }
                                                } else if (userByUid.name != userName){
                                                    url.path.value = listOf(teamUrl, teamName, userByUid.name)
                                                } else {
                                                    //show controls
                                                    h4().text("You have eaten ")
                                                    h4().text(toVar(State.users, uid).map(State.User::nuggetCount))
                                                    h4().text(" nuggets")
                                                    div(s.ui.action.input).new {
                                                        button(s.ui.button).text("NOM").on.click{
                                                            logger.debug("clicked NOM")
                                                            State.users.modify(uid){
                                                                val newCount = userByUid.nuggetCount.toInt().inc()
                                                                logger.debug("new count: $newCount")
                                                                State.User(uid, teamName, userName, newCount.toString())
                                                            }
                                                        }
                                                        button(s.ui.button).text("undo").on.click{
                                                            logger.debug("Clicked undo")
                                                            State.users.modify(uid){
                                                                var newCount = userByUid.nuggetCount.toInt().dec()
                                                                if(newCount < 0){
                                                                    newCount = 0
                                                                }
                                                                State.User(uid, teamName, userName, newCount.toString())
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
        url.path.value = listOf(teamUrl, name)
    }
}

private fun ElementCreator<*>.renderTeam(team: KVar<State.Team>) {
    //val users = State.usersByTeam(team.value.uid)
    h3().new{
        div().text("Team ")
        div().text(team.map(State.Team::uid))
        div().text(" has ")
        div().text(team.map(State.Team::nuggetsRemaining))
        p().text(" nuggets remaining.")
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