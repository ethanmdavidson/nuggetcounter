package com.machinerychorus.nuggetcounter

import io.kweb.*
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
import io.kweb.state.persistent.renderEach
import io.kweb.state.persistent.toVar
import io.mola.galimatias.URL
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.CompletableFuture
import io.kweb.plugins.semanticUI.semantic as s

private val logger = KotlinLogging.logger {}
const val teamUrl = "team"
const val loginUrl = "login"
const val userUrl = "user"
const val uidKey = "uid"

fun main(args: Array<String>) {
    val plugins = listOf(semanticUIPlugin, jsCookiePlugin)

    Kweb(port = 8081, debug = false, plugins = plugins) {
        doc.body.new {
            pageBorderAndTitle("\uD83D\uDC14 Nugget Counter \uD83C\uDF57") {

                val url: KVar<URL> = doc.receiver.url(simpleUrlParser)

                div(s.content.centered).new {

                    val firstPathElement: KVar<String> = url.path[0]

                    render(firstPathElement) { entityType ->
                        when (entityType) {
                            ROOT_PATH -> {
                                div(s.ui.action.input).new {
                                    //TODO: list existing teams?
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
                                                //if UID exists and matches a user, redirect to that user
                                                GlobalScope.launch {
                                                    val uid = getString(doc.cookie.receiver, uidKey).await()
                                                    if (uid != null && uid.isNotEmpty() && State.users[uid] != null) {
                                                        val user = State.users[uid]
                                                            ?: throw NullPointerException("Couldn't load user $uid")
                                                        logger.info("/login called for existing user ${user.name} (${user.uid})")
                                                        url.path.value = listOf(teamUrl, teamName, userUrl, user.name)
                                                    } else {
                                                        //otherwise show login
                                                        logger.info("Showing login for team '$teamName'")
                                                        div().new {
                                                            h4().text("Join this team:")
                                                            inputWithButton("Username", "Join!") {
                                                                val newUid = generateNewUid()
                                                                //it.jsCookie().set(uidKey, newUid)
                                                                doc.cookie.setString(uidKey, newUid)
                                                                logger.debug("Created new UID: $newUid")
                                                                GlobalScope.launch {
                                                                    val newUsername = it.getValue().await()
                                                                    State.users[newUid] =
                                                                            State.User(newUid, teamName, newUsername)
                                                                    logger.info("Created user '$newUsername' with uid '$newUid' for team '$teamName'")
                                                                    url.path.value = listOf(
                                                                        teamUrl,
                                                                        teamName,
                                                                        userUrl,
                                                                        newUsername
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            userUrl -> {
                                                GlobalScope.launch {
                                                    val uid = getString(doc.cookie.receiver, uidKey).await()
                                                    //if UID doesn't exist or doesn't match this user, redirect to login
                                                    if (uid == null
                                                        || uid.isEmpty()
                                                        || State.users[uid] == null
                                                        || url.path.value.size < 4
                                                        || url.path[3].value != State.users[uid]!!.name
                                                    ) {
                                                        logger.info("/user called with incomplete credentials, uid '$uid'")
                                                        url.path.value = listOf(teamUrl, teamName, loginUrl)
                                                    } else {
                                                        //otherwise show controls
                                                        logger.info("loaded user page for ${url.path[3].value} ($uid)")
                                                        h4().text(toVar(State.users, uid).map {
                                                            "You have eaten ${it.nuggetCount} nuggets"
                                                        })
                                                        div(s.ui.action.input).new {
                                                            button(s.ui.button).text("NOM").on.click {
                                                                State.users.modify(uid) { user ->
                                                                    val newCount = user.nuggetCount.inc()
                                                                    State.User(
                                                                        user.uid,
                                                                        user.teamUid,
                                                                        user.name,
                                                                        newCount
                                                                    )
                                                                }
                                                            }
                                                            button(s.ui.button).text("undo").on.click {
                                                                State.users.modify(uid) { user ->
                                                                    logger.debug("count before: ${user.nuggetCount}")
                                                                    var newCount = user.nuggetCount.dec()
                                                                    logger.debug("new count: ${newCount}")
                                                                    if (newCount < 0) {
                                                                        newCount = 0
                                                                    }
                                                                    State.User(
                                                                        user.uid,
                                                                        user.teamUid,
                                                                        user.name,
                                                                        newCount
                                                                    )
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
    div(s.container).new {
        div(s.ui.one.column.center.aligned.grid).new {
            div(s.column).new {
                div(s.divider.hidden)   //cheap way to add some margin to the top
                h1(s.ui.dividing.header).text(title)
                content(this)
            }
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
    h3(s.center).text(team.map{ "Team ${it.uid} has ${it.nuggetsRemaining} nuggets remaining."})
    div(s.ui.middle.aligned.divided.list).new {
        try {
            renderEach(State.usersByTeam(team.value.uid)) { user ->
                div(s.item).new {
                    div(s.content).text(user.map { "${it.name} : ${it.nuggetCount}" })
                }
            }
        } catch(e:RuntimeException){
            logger.error("Failed the renderEach", e)
        }
    }
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

/**
 * There is a typo in the js used to work with cookies.
 * The function is docCookies.getItems (plural) but CookieReceiver.getString is
 *  calling docCookies.getItem (singular)
 *  https://github.com/kwebio/core/issues/41
 */
fun getString(receiver: WebBrowser, name: String): CompletableFuture<String?> {
    return receiver.evaluate("docCookies.getItems(${name.toJson()});")
        .thenApply {
            if (it == "__COOKIE_NOT_FOUND_TOKEN__") {
                null
            } else {
                it
            }
        }
}