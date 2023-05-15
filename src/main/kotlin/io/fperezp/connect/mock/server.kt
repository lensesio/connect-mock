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


data class ConnectorCreateRequest(val name: String, val config: Map<String, String>)

data class ConnectorResponse(
    val name: String,
    val config: Map<String, String>,
    val tasks: List<ConnectorTask>,
)

data class ConnectorInstanceStatus(val state: ConnectStatus, val worker_id: String)
data class ConnectorStatusResponse(
    val name: String, val connector: ConnectorInstanceStatus, val tasks: List<TaskStatus>
)

data class ConnectorInfo(val name: String, val config: Map<String, String>, val tasks: List<ConnectorTask>)
data class ConnectorTasksAndInfo(val status: ConnectorStatusResponse, val info: ConnectorInfo)
data class ConnectorsTasksAndInfo(val connectors: Map<String, ConnectorTasksAndInfo>)

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
    RUNNING, PAUSED, FAILED, UNASSIGNED, RESTARTING
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

data class ConnectorConfigAndStatus(val config: Map<String, String>, val status: List<TaskStatus>)

fun Application.main() {

    val clusterId: String = UUID.randomUUID().toString()
    val connectors = Atomic.unsafe(emptyMap<String, ConnectorConfigAndStatus>())

    configureJsonSerialization()
    configureErrorInterceptor()
    install(CallLogging)
    install(DefaultHeaders)
    install(AutoHeadResponse)
    install(XForwardedHeaderSupport)
    install(ForwardedHeaderSupport)

    routing {
        //trace { application.log.trace(it.buildText()) }

        route("/") {
            get {
                call.respond(
                    HttpStatusCode.OK, Info("2.5.1-L0", "0efa8fb0f4c73d92", "Sr6uNBjPRN-AH8B10EtF2A")
                )
            }
        }


        route("/connector-plugins") {
            get {
                call.respond(
                    HttpStatusCode.OK, listOf(
                        Plugin("io.lenses.runner.connect.ProcessorConnector", "sink", "mock"),
                        Plugin("com.landoop.connect.SQL", "sink", "mock")
                    )
                )
            }
        }

        // give the curl command to populate the connector
        // curl -X POST -H "Content-Type: application/json" -d '{"name":"connector1","config":{"connector.class":"io.lenses.runner.connect.ProcessorConnector","tasks.max":"1","topics":"topic1","connector.id":"connector1","name":"connector1","connect.sql":"select * from topic1","connect.sql.schema":"{\"type\":\"record\",\"name\":\"myrecord\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"},{\"name\":\"f2\",\"type\":\"int\"}]}"}}' http://localhost:8083/connectors
        route("/connectors") {
            post {
                val request = call.receive<ConnectorCreateRequest>()
                val tasksMax: Int = request.config["tasks.max"]?.toInt() ?: 0

                connectors.update {
                    (it + (request.name to ConnectorConfigAndStatus(
                        request.config, buildTasksDetailsList(tasksMax)
                    )))
                }

                call.respond(
                    HttpStatusCode.Created,
                    ConnectorResponse(request.name, request.config, buildTasksList(request.name, tasksMax))
                )
            }

            // give the curl command to populate the connector
            // curl -X GET http://localhost:8083/connectors | jq
            get {
                val state = connectors.get()
                //check if query param expand is present and if not return only the connector names otherwise return the full connector info
                if (call.request.queryParameters["expand"] == null) {
                    call.respond(
                        HttpStatusCode.OK, state.keys
                    )
                } else {
                    val response = ConnectorsTasksAndInfo(state.mapValues {
                        ConnectorTasksAndInfo(
                            ConnectorStatusResponse(
                                it.key, ConnectorInstanceStatus(ConnectStatus.RUNNING, "127.0.0.1"), it.value.status
                            ), ConnectorInfo(it.key,
                                it.value.config,
                                it.value.status.map { t -> ConnectorTask(it.key, t.id) })
                        )
                    })

                    call.respond(
                        HttpStatusCode.OK, response
                    )
                }
            }

            route("{connectorName}") {
                get {
                    val connectorName = call.parameters.getOrFail("connectorName")
                    val state = connectors.get()[connectorName]?.status ?: emptyList()
                    val response = ConnectorInfo(connectorName,
                        connectors.get()[connectorName]?.config ?: emptyMap(),
                        state.map { ConnectorTask(connectorName, it.id) })

                    call.respond(HttpStatusCode.OK, response)
                }

                route("restart") {
                    post {
                        val connectorName = call.parameters.getOrFail("connectorName")
                        val state = connectors.get()[connectorName]?.status ?: emptyList()
                        call.respond(
                            HttpStatusCode.OK, ConnectorStatusResponse(connectorName,
                                ConnectorInstanceStatus(ConnectStatus.RESTARTING, "127.0.0.1"),
                                state.map { it.copy(state = ConnectStatus.RESTARTING) })
                        )
                    }
                }
                route("status") {
                    put {
                        val connectorName = call.parameters.getOrFail("connectorName")
                        val request = call.receive<Array<TaskStatus>>()
                        val state = connectors.updateAndGet {
                            //only update if the connector exists
                            if (it.containsKey(connectorName)) {
                                val config = it[connectorName]?.config ?: emptyMap()
                                val newConfig = config + ("tasks.max" to request.size.toString())
                                it + (connectorName to ConnectorConfigAndStatus(newConfig, request.toList()))

                            } else {
                                it
                            }
                        }
                        val response = ConnectorStatusResponse(
                            connectorName,
                            ConnectorInstanceStatus(ConnectStatus.RUNNING, "127.0.0.1"),
                            state[connectorName]?.status?.toList() ?: emptyList()
                        )

                        call.respond(HttpStatusCode.OK, response)
                    }

                    get {
                        val connectorName = call.parameters.getOrFail("connectorName")
                        val state = connectors.get()
                        if (state.containsKey(connectorName)) {
                            val response = ConnectorStatusResponse(
                                connectorName,
                                ConnectorInstanceStatus(ConnectStatus.RUNNING, "127.0.0.1"),
                                state[connectorName]?.status ?: emptyList()
                            )
                            call.respond(
                                HttpStatusCode.OK, response
                            )
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                ConnectorError(clusterId, 404, ErrorMessage("Connector $connectorName not found"))
                            )
                        }
                    }


                }

                route("config") {
                    put {
                        val request = call.receive<ConnectorCreateRequest>()

                        val tasksMax: Int = request.config["tasks.max"]?.toInt() ?: 0
                        val connectorName = call.parameters.getOrFail("connectorName")
                        connectors.update {
                            (it + (request.name to ConnectorConfigAndStatus(
                                request.config, buildTasksDetailsList(tasksMax)
                            )))
                        }

                        call.respond(
                            HttpStatusCode.OK,
                            ConnectorResponse(request.name, request.config, buildTasksList(connectorName, tasksMax))
                        )
                    }

                    get {
                        val connectorName = call.parameters.getOrFail("connectorName")
                        val connectorConfig: Map<String, String> = connectors.get()[connectorName]?.config ?: emptyMap()

                        call.respond(HttpStatusCode.OK, connectorConfig)
                    }
                }
            }
        }
    }
}

private fun buildTasksDetailsList(tasksMax: Int) = List(tasksMax) {
    TaskStatus(it, ConnectStatus.FAILED, "127.0.0.1")
}

private fun buildTasksList(connectorName: String, tasksMax: Int) = List(tasksMax) {
    ConnectorTask(connectorName, it)
}

private fun Application.configureErrorInterceptor() {
    install(StatusPages) {
        exception<NotFoundException> { cause ->
            call.respond(HttpStatusCode.NotFound, "Not found: ${cause.message}")
            throw cause
        }
        exception<MissingRequestParameterException> { cause ->
            call.respond(HttpStatusCode.NotFound, "Not found: ${cause.message}")
            throw cause
        }
        exception<Throwable> { cause ->
            call.respond(HttpStatusCode.InternalServerError, "Internal Server Error: ${cause.message}")
            throw cause
        }
    }
}

private fun Application.configureJsonSerialization() {
    install(ContentNegotiation) {
        //register(ContentType.Application.Json, JacksonConverter())
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
}