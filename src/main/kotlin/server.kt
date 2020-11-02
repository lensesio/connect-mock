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
data class ConnectorCreateRequest(val name: String, val config: Map<String, String>)
data class ConnectorStatusResponse(val name: String, val tasks: List<TaskStatus>)
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

    embeddedServer(Netty, port = 8080, host = "127.0.0.1") {

        configureJsonSerialization()

        routing {
            get("/") {
                call.respond(
                    HttpStatusCode.OK,
                    Info("CP-2.5-mocked", "mocked-commit-id", clusterId)
                )
            }

            get("/connectors/{connectorName}/status") {
                call.respond(
                    HttpStatusCode.OK,
                    ConnectorStatusResponse(
                        call.parameters.getOrFail("connectorName"),
                        listOf(TaskStatus(0, ConnectStatus.RUNNING, "127.0.0.1", null))
                    )
                )
            }

            post("/connectors") {
                val request = call.receive<ConnectorCreateRequest>()
                call.respond(request)
            }
        }
    }.start(wait = true)
}

private fun Application.configureJsonSerialization() {
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
}