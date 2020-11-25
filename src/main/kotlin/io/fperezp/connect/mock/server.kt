package io.fperezp.connect.mock

import arrow.fx.coroutines.Atomic
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import java.util.*


data class Info(val version: String, val commit: String, val kafkaClusterId: String)
data class Plugin(val className: String, val pluginType: String?, val version: String?)
data class ConnectorCreateRequest(val name: String, val config: Map<String, String>)
data class ConnectorStatusResponse(val name: String, val tasks: List<TaskStatus>)
data class ConnectorResponse(val name: String, val config: Map<String, String>, val tasks: List<TaskStatus>)
data class TaskStatus(
    val id: Int,
    val status: ConnectStatus,
    val workerId: String,
    val message: String?
)

enum class ConnectStatus {
    RUNNING, PAUSED, FAILED, UNASSIGNED
}

fun main() {

    val clusterId: String = UUID.randomUUID().toString()
    val connectors = Atomic.unsafe(emptyMap<String, Map<String, String>>())

    embeddedServer(Netty, port = 8083, host = "127.0.0.1") {

        configureJsonSerialization()
        install(CallLogging)
        install(DefaultHeaders)

        routing {
            get("/") {
                call.respond(
                    HttpStatusCode.OK,
                    Info("CP-2.5-mocked", "mocked-commit-id", clusterId)
                )
            }

            get("/connector-plugins") {
                call.respond(
                    HttpStatusCode.OK,
                    listOf(
                        Plugin("io.lenses.runner.connect.ProcessorConnector", "sink", "mock"),
                        Plugin("com.landoop.connect.SQL", "sink", "mock")
                    )
                )
            }

            post("/connectors") {
                val request = call.receive<ConnectorCreateRequest>()
                connectors.update { (it + (request.name to request.config)) }
                val tasksMax: Int = request.config["tasks.max"]?.toInt() ?: 0

                call.respond(
                    HttpStatusCode.OK,
                    ConnectorResponse(request.name, request.config, buildTasksList(tasksMax))
                )
            }

            put("/connectors") {
                val request = call.receive<ConnectorCreateRequest>()
                connectors.update { (it + (request.name to request.config)) }
                val tasksMax: Int = request.config["tasks.max"]?.toInt() ?: 0

                call.respond(
                    HttpStatusCode.OK,
                    ConnectorResponse(request.name, request.config, buildTasksList(tasksMax))
                )

                call.respond(request)
            }

            get("/connectors") {
                call.respond(
                    HttpStatusCode.OK,
                    connectors.get().keys
                )
            }

            get("/connectors/{connectorName}/") {
                val connectorName = call.parameters.getOrFail("connectorName")
                val tasksMax: Int = connectors.get()[connectorName]?.get("tasks.max")?.toInt() ?: 0
                val config: Map<String, String> = connectors.get()[connectorName] ?: emptyMap()

                call.respond(HttpStatusCode.OK, ConnectorResponse(connectorName, config, buildTasksList(tasksMax)))
            }

            get("/connectors/{connectorName}/status") {
                val connectorName = call.parameters.getOrFail("connectorName")
                val tasksMax: Int = connectors.get()[connectorName]?.get("tasks.max")?.toInt() ?: 0
                call.respond(HttpStatusCode.OK, ConnectorStatusResponse(connectorName, buildTasksList(tasksMax)))
            }


            get("/connectors/{connectorName}/config") {
                val connectorName = call.parameters.getOrFail("connectorName")
                val connectorConfig: Map<String, String> = connectors.get()[connectorName] ?: emptyMap()

                call.respond(HttpStatusCode.OK, connectorConfig)
            }
        }
    }.start(wait = true)
}

private fun buildTasksList(tasksMax: Int) = List(tasksMax) {
    TaskStatus(it, ConnectStatus.RUNNING, "127.0.0.1", null)
}

private fun Application.configureJsonSerialization() {
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
}