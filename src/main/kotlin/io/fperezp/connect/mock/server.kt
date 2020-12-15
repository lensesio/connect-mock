package io.fperezp.connect.mock

import arrow.fx.coroutines.Atomic
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


data class ConnectorCreateRequest(val name: String, val config: Map<String, String>)

data class ConnectorResponse(
    val name: String,
    val config: Map<String, String>,
    val tasks: List<ConnectorTask>,
)

data class ConnectorStatusResponse(val name: String, val tasks: List<TaskStatus>)

// State to be moved to an Enum when working on https://landoop.atlassian.net/browse/FLOWS-1365
// This has been created now for testing Deployments, but it's not a final version and will be reworked
// as part of the ticket mentioned above.
data class TaskStatus(
    val id: Int,
    val state: ConnectStatus,
    val worker_id: String,
    val trace: String? = null,
)

data class Info(val version: String, val commit: String, val kafka_cluster_id: String)

data class Plugin(val `class`: String, val `type`: String, val version: String)

data class InvalidConnectResponse(val message: String, val code: Int, val error: Throwable? = null)

data class ConnectorTask(val connector: String, val task: Int)
data class ConnectorError(val clusterName: String, val statusCode: Int, val message: ErrorMessage)
data class ErrorMessage(val message: String)
data class ConnectInfo(val name: String, val version: String)
data class ConnectorPlugin(val className: String, val pluginType: String, val version: String)


enum class ConnectStatus {
    RUNNING, PAUSED, FAILED, UNASSIGNED
}

data class Config(val workerAPort: Int, val workerBPort: Int, val workerCPort: Int)

fun main() {
    val config = Config(8083, 8183, 8283)

//    val config = ConfigLoader().loadConfig<Config>("application.conf").getUnsafe()

    val env = applicationEngineEnvironment {
        module {
            main()
        }
        // WorkerA
        connector {
            host = "0.0.0.0"
            port = config.workerAPort
        }
        // WorkerB
        connector {
            host = "0.0.0.0"
            port = config.workerBPort
        }
        // WorkerC
        connector {
            host = "0.0.0.0"
            port = config.workerCPort
        }
    }
    embeddedServer(Netty, env).start(true)
}

fun Application.main() {

    val clusterId: String = UUID.randomUUID().toString()
    val connectors = Atomic.unsafe(emptyMap<String, Map<String, String>>())

    configureJsonSerialization()
    configureErrorInterceptor()
    install(CallLogging)
    install(DefaultHeaders)
    install(AutoHeadResponse)
    install(XForwardedHeaderSupport)
    install(ForwardedHeaderSupport)

    routing {
        route("/") {
            get {
                call.respond(
                    HttpStatusCode.OK,
                    Info("2.5.1-L0", "0efa8fb0f4c73d92", "Sr6uNBjPRN-AH8B10EtF2A")
                )
            }
        }


        route("/connector-plugins") {
            get {
                call.respond(
                    HttpStatusCode.OK,
                    listOf(
                        Plugin("io.lenses.runner.connect.ProcessorConnector", "sink", "mock"),
                        Plugin("com.landoop.connect.SQL", "sink", "mock")
                    )
                )
            }
        }

        route("/connectors") {
            post {
                val request = call.receive<ConnectorCreateRequest>()
                connectors.update { (it + (request.name to request.config)) }
                val tasksMax: Int = request.config["tasks.max"]?.toInt() ?: 0
                call.respond(
                    HttpStatusCode.Created,
                    ConnectorResponse(request.name, request.config, buildTasksList(request.name, tasksMax))
                )
            }

            get {
                val connectorNames = connectors.get().keys
                call.respond(
                    HttpStatusCode.OK,
                    connectorNames
                )
            }

            route("{connectorName}") {
                get("status") {
                    val connectorName = call.parameters.getOrFail("connectorName")
                    val tasksMax: Int = connectors.get()[connectorName]?.get("tasks.max")?.toInt() ?: 0
                    call.respond(
                        HttpStatusCode.OK,
                        ConnectorStatusResponse(connectorName, buildTasksDetailsList(tasksMax))
                    )
                }


                route("config") {
                    put {
                        val request = call.receive<ConnectorCreateRequest>()
                        connectors.update { (it + (request.name to request.config)) }
                        val tasksMax: Int = request.config["tasks.max"]?.toInt() ?: 0
                        val connectorName = call.parameters.getOrFail("connectorName")


                        call.respond(
                            HttpStatusCode.OK,
                            ConnectorResponse(request.name, request.config, buildTasksList(connectorName, tasksMax))
                        )
                    }

                    get {
                        val connectorName = call.parameters.getOrFail("connectorName")
                        val connectorConfig: Map<String, String> = connectors.get()[connectorName] ?: emptyMap()

                        call.respond(HttpStatusCode.OK, connectorConfig)
                    }
                }
            }
        }


        get("/http:/localhost:8083/") {
            call.respond(
                HttpStatusCode.OK,
                Info("2.5.1-L0", "0efa8fb0f4c73d92", "Sr6uNBjPRN-AH8B10EtF2A")
            )
        }

        get("/http:/localhost:8083/connector-plugins") {
            call.respond(
                HttpStatusCode.OK,
                listOf(
                    Plugin("io.lenses.runner.connect.ProcessorConnector", "sink", "mock"),
                    Plugin("com.landoop.connect.SQL", "sink", "mock")
                )
            )
        }

        route("/http:/localhost:8083/connectors") {

            post {
                val request = call.receive<ConnectorCreateRequest>()
                connectors.update { (it + (request.name to request.config)) }
                val tasksMax: Int = request.config["tasks.max"]?.toInt() ?: 0
                call.respond(
                    HttpStatusCode.Created,
                    ConnectorResponse(request.name, request.config, buildTasksList(request.name, tasksMax))
                )
            }

            get {
                call.respond(
                    HttpStatusCode.OK,
                    connectors.get().keys
                )
            }

            route("{connectorName}") {

                get {
                    val connectorName: String = call.parameters.getOrFail("connectorName")
                    val config = connectors.get()[connectorName]
                        ?: throw NotFoundException("Connector [$connectorName] not found")
                    val tasksMax = config["tasks.max"]?.toInt() ?: 0


                    call.respond(
                        HttpStatusCode.OK,
                        ConnectorResponse(connectorName, config, buildTasksList(connectorName, tasksMax))
                    )
                }

                get("status") {
                    val connectorName = call.parameters.getOrFail("connectorName")
                    val tasksMax: Int = connectors.get()[connectorName]?.get("tasks.max")?.toInt() ?: 0
                    call.respond(
                        HttpStatusCode.OK,
                        ConnectorStatusResponse(connectorName, buildTasksDetailsList(tasksMax))
                    )
                }


                route("config") {
                    get {
                        val connectorName = call.parameters.getOrFail("connectorName")
                        val connectorConfig: Map<String, String> = connectors.get()[connectorName] ?: emptyMap()

                        call.respond(HttpStatusCode.OK, connectorConfig)
                    }

                    put {
                        val request = call.receive<ConnectorCreateRequest>()
                        connectors.update { (it + (request.name to request.config)) }
                        val connectorName = call.parameters.getOrFail("connectorName")
                        val tasksMax: Int = request.config["tasks.max"]?.toInt() ?: 0

                        call.respond(
                            HttpStatusCode.OK,
                            ConnectorResponse(request.name, request.config, buildTasksList(connectorName, tasksMax))
                        )
                    }
                }
            }
        }
    }
}

private fun buildTasksDetailsList(tasksMax: Int) = List(tasksMax) {
    TaskStatus(it, ConnectStatus.RUNNING, "127.0.0.1")
}

private fun buildTasksList(connectorName: String, tasksMax: Int) = List(tasksMax) {
    ConnectorTask(connectorName, it)
}

private fun Application.configureErrorInterceptor() {
    install(StatusPages) {
        exception<NotFoundException> { cause ->
            call.respond(HttpStatusCode.NotFound, "------Not found: ${cause.message}")
            throw cause
        }
        exception<MissingRequestParameterException> { cause ->
            call.respond(HttpStatusCode.NotFound, "------Not found: ${cause.message}")
            throw cause
        }
        exception<Throwable> { cause ->
            call.respond(HttpStatusCode.InternalServerError, "Internal Server Error")
            throw cause
        }
    }
}

private fun Application.configureJsonSerialization() {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter())
    }
}