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

data class ConnectorInstanceStatus(val state: ConnectStatus, val worker_id: String, val trace: String? = null)
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

data class Plugin(
    val `class`: String,
    val `type`: String,
    val version: String,
    val description: String = "lorem ipsum",
    val docs: String = "https://docs.lenses.io/connectors/sink/processor.html",
    val icon: String = "s3.png",
    val name: String = "S3 Sink",
    val uiEnabled: Boolean = true,
    val author: String = "m"
)

data class InvalidConnectResponse(val message: String, val code: Int, val error: Throwable? = null)

data class ConnectorTask(val connector: String, val task: Int)
data class ConnectorError(val clusterName: String, val statusCode: Int, val message: ErrorMessage)
data class ErrorMessage(val message: String)
data class ConnectInfo(val name: String, val version: String)
data class ConnectorPlugin(val className: String, val pluginType: String, val version: String)

data class ConnectorTaskResponse(val id: ConnectorTask, val config: Map<String, String>)
data class ConnectorTopics(val topics:List<String>)

enum class ConnectStatus {
    RUNNING, PAUSED, FAILED, UNASSIGNED, RESTARTING
}

data class Config(val workerAPort: Int, val workerBPort: Int, val workerCPort: Int)


fun main() {
    val config = Config(18083, 18183, 18283)

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
    val connectors = Atomic.unsafe(emptyMap<String, ConnectorTasksAndInfo>())

    configureJsonSerialization()
    configureErrorInterceptor()
    install(CallLogging)
    install(DefaultHeaders)
    install(AutoHeadResponse)
    install(XForwardedHeaderSupport)
    install(ForwardedHeaderSupport)
    install(Compression) {
        gzip()
        deflate()
    }

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
                //respond with this json content
                val json = """
                   [
                     {
                       "class": "com.daimler.connect.db2.jdbc.json.SinkConnector",
                       "type": "sink",
                       "version": "1.4.1"
                     },
                     {
                       "class": "com.daimler.gsep.sinkconnector.jira.kafkaconnector.JiraSinkConnector",
                       "type": "sink",
                       "version": "null"
                     },
                     {
                       "class": "com.daimler.ibm.eventstreams.connect.mqsink.MQSinkConnector",
                       "type": "sink",
                       "version": "1.3.1"
                     },
                     {
                       "class": "com.daimler.microsoft.azure.eventhubs.kafka.connect.sink.EventHubSinkConnector",
                       "type": "sink",
                       "version": "1.2.0"
                     },
                     {
                       "class": "com.daimler.starc.kafka.sinkconnector.jira.connector.JiraStarcSinkConnector",
                       "type": "sink",
                       "version": "null"
                     },
                     {
                       "class": "com.daimler.xmp.connect.KafkaSinkConnector",
                       "type": "sink",
                       "version": "v0.1.2"
                     },
                     {
                       "class": "com.datamountaineer.streamreactor.connect.cassandra.sink.CassandraSinkConnector",
                       "type": "sink",
                       "version": "3.0.1"
                     },
                     {
                       "class": "com.datamountaineer.streamreactor.connect.cassandra.sink.CassandraSinkConnector",
                       "type": "sink",
                       "version": "5.0.1"
                     },
                     {
                       "class": "com.datamountaineer.streamreactor.connect.elastic6.ElasticSinkConnector",
                       "type": "sink",
                       "version": "3.0.1"
                     },
                     {
                       "class": "com.ibm.eventstreams.connect.mqsink.MQSinkConnector",
                       "type": "sink",
                       "version": "1.0.2"
                     },
                     {
                       "class": "com.ibm.eventstreams.connect.mqsink.MQSinkConnector",
                       "type": "sink",
                       "version": "1.5.2"
                     },
                     {
                       "class": "com.mercedes.benz.azure.eventhub.kafka.sink.EventHubSinkConnector",
                       "type": "sink",
                       "version": "1.0-SNAPSHOT"
                     },
                     {
                       "class": "com.mongodb.kafka.connect.MongoSinkConnector",
                       "type": "sink",
                       "version": "1.6.1"
                     },
                     {
                       "class": "com.mongodb.kafka.connect.MongoSinkConnector",
                       "type": "sink",
                       "version": "1.11.0"
                     },
                     {
                       "class": "com.sap.kafka.connect.sink.hana.HANASinkConnector",
                       "type": "sink",
                       "version": "null"
                     },
                     {
                       "class": "com.splunk.kafka.connect.SplunkSinkConnector",
                       "type": "sink",
                       "version": "v2.1.1"
                     },
                     {
                       "class": "io.aiven.kafka.connect.opensearch.OpensearchSinkConnector",
                       "type": "sink",
                       "version": "2.0.4"
                     },
                     {
                       "class": "io.confluent.connect.azure.datalake.gen2.AzureDataLakeGen2SinkConnector",
                       "type": "sink",
                       "version": "1.6.17"
                     },
                     {
                       "class": "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector",
                       "type": "sink",
                       "version": "11.1.7"
                     },
                     {
                       "class": "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector",
                       "type": "sink",
                       "version": "14.0.10"
                     },
                     {
                       "class": "io.confluent.connect.jdbc.JdbcSinkConnector",
                       "type": "sink",
                       "version": "10.7.4"
                     },
                     {
                       "class": "io.confluent.connect.s3.S3SinkConnector",
                       "type": "sink",
                       "version": "10.5.7"
                     },
                     {
                       "class": "io.confluent.connect.salesforce.SalesforceBulkApiSinkConnector",
                       "type": "sink",
                       "version": "2.0.10"
                     },
                     {
                       "class": "io.confluent.connect.salesforce.SalesforceBulkApiSinkConnector",
                       "type": "sink",
                       "version": "2.0.13"
                     },
                     {
                       "class": "io.confluent.connect.servicenow.ServiceNowSinkConnector",
                       "type": "sink",
                       "version": "2.4.5"
                     },
                     {
                       "class": "io.confluent.connect.servicenow.ServiceNowSinkConnector",
                       "type": "sink",
                       "version": "2.5.0"
                     },
                     {
                       "class": "io.confluent.connect.sftp.SftpSinkConnector",
                       "type": "sink",
                       "version": "unknown"
                     },
                     {
                       "class": "io.confluent.salesforce.SalesforcePlatformEventSinkConnector",
                       "type": "sink",
                       "version": "2.0.1"
                     },
                     {
                       "class": "io.confluent.salesforce.SalesforcePlatformEventSinkConnector",
                       "type": "sink",
                       "version": "2.0.13"
                     },
                     {
                       "class": "io.confluent.salesforce.SalesforceSObjectSinkConnector",
                       "type": "sink",
                       "version": "2.0.1"
                     },
                     {
                       "class": "io.confluent.salesforce.SalesforceSObjectSinkConnector",
                       "type": "sink",
                       "version": "2.0.13"
                     },
                     {
                       "class": "org.init.ohja.kafka.connect.adso.sink.ADSOSinkConnector",
                       "type": "sink",
                       "version": "1.3.3-109-d258410f"
                     },
                     {
                       "class": "org.init.ohja.kafka.connect.odatav2.sink.OData2SinkConnector",
                       "type": "sink",
                       "version": "1.3.3-265-7fb99475"
                     },
                     {
                       "class": "com.daimler.connect.db2.jdbc.json.SourceConnector",
                       "type": "source",
                       "version": "1.4.1"
                     },
                     {
                       "class": "com.datamountaineer.streamreactor.connect.cassandra.source.CassandraSourceConnector",
                       "type": "source",
                       "version": "3.0.1"
                     },
                     {
                       "class": "com.datamountaineer.streamreactor.connect.cassandra.source.CassandraSourceConnector",
                       "type": "source",
                       "version": "5.0.1"
                     },
                     {
                       "class": "com.ecer.kafka.connect.oracle.OracleSourceConnector",
                       "type": "source",
                       "version": "1.0.68"
                     },
                     {
                       "class": "com.github.jcustenborder.kafka.connect.spooldir.SpoolDirAvroSourceConnector",
                       "type": "source",
                       "version": "0.0.0.0"
                     },
                     {
                       "class": "com.github.jcustenborder.kafka.connect.spooldir.SpoolDirBinaryFileSourceConnector",
                       "type": "source",
                       "version": "0.0.0.0"
                     },
                     {
                       "class": "com.github.jcustenborder.kafka.connect.spooldir.SpoolDirCsvSourceConnector",
                       "type": "source",
                       "version": "0.0.0.0"
                     },
                     {
                       "class": "com.github.jcustenborder.kafka.connect.spooldir.SpoolDirJsonSourceConnector",
                       "type": "source",
                       "version": "0.0.0.0"
                     },
                     {
                       "class": "com.github.jcustenborder.kafka.connect.spooldir.SpoolDirLineDelimitedSourceConnector",
                       "type": "source",
                       "version": "0.0.0.0"
                     },
                     {
                       "class": "com.github.jcustenborder.kafka.connect.spooldir.SpoolDirSchemaLessJsonSourceConnector",
                       "type": "source",
                       "version": "0.0.0.0"
                     },
                     {
                       "class": "com.github.jcustenborder.kafka.connect.spooldir.elf.SpoolDirELFSourceConnector",
                       "type": "source",
                       "version": "0.0.0.0"
                     },
                     {
                       "class": "com.ibm.eventstreams.connect.mqsource.MQSourceConnector",
                       "type": "source",
                       "version": "1.1.0"
                     },
                     {
                       "class": "com.ibm.eventstreams.connect.mqsource.MQSourceConnector",
                       "type": "source",
                       "version": "1.3.4"
                     },
                     {
                       "class": "com.landoop.connect.SQL",
                       "type": "source",
                       "version": "v3.2.1-kafka2.5"
                     },
                     {
                       "class": "com.mbcpos.ibm.eventstreams.connect.mqsource.MQSourceConnector",
                       "type": "source",
                       "version": "1.3.1"
                     },
                     {
                       "class": "com.mongodb.kafka.connect.MongoSourceConnector",
                       "type": "source",
                       "version": "1.6.1"
                     },
                     {
                       "class": "com.mongodb.kafka.connect.MongoSourceConnector",
                       "type": "source",
                       "version": "1.11.0"
                     },
                     {
                       "class": "com.sap.kafka.connect.source.hana.HANASourceConnector",
                       "type": "source",
                       "version": "null"
                     },
                     {
                       "class": "io.confluent.connect.azure.servicebus.ServiceBusSourceConnector",
                       "type": "source",
                       "version": "1.2.5"
                     },
                     {
                       "class": "io.confluent.connect.ibm.mq.IbmMQSourceConnector",
                       "type": "source",
                       "version": "12.2.1"
                     },
                     {
                       "class": "io.confluent.connect.jdbc.JdbcSourceConnector",
                       "type": "source",
                       "version": "10.7.4"
                     },
                     {
                       "class": "io.confluent.connect.replicator.ReplicatorSourceConnector",
                       "type": "source"
                     },
                     {
                       "class": "io.confluent.connect.salesforce.SalesforceBulkApiSourceConnector",
                       "type": "source",
                       "version": "2.0.10"
                     },
                     {
                       "class": "io.confluent.connect.salesforce.SalesforceBulkApiSourceConnector",
                       "type": "source",
                       "version": "2.0.13"
                     },
                     {
                       "class": "io.confluent.connect.servicenow.ServiceNowSourceConnector",
                       "type": "source",
                       "version": "2.4.5"
                     },
                     {
                       "class": "io.confluent.connect.servicenow.ServiceNowSourceConnector",
                       "type": "source",
                       "version": "2.5.0"
                     },
                     {
                       "class": "io.confluent.connect.sftp.SftpBinaryFileSourceConnector",
                       "type": "source",
                       "version": "0.0.0.0"
                     },
                     {
                       "class": "io.confluent.connect.sftp.SftpCsvSourceConnector",
                       "type": "source",
                       "version": "0.0.0.0"
                     },
                     {
                       "class": "io.confluent.connect.sftp.SftpGenericSourceConnector",
                       "type": "source",
                       "version": "0.0.0.0"
                     },
                     {
                       "class": "io.confluent.connect.sftp.SftpJsonSourceConnector",
                       "type": "source",
                       "version": "0.0.0.0"
                     },
                     {
                       "class": "io.confluent.connect.sftp.SftpSchemaLessJsonSourceConnector",
                       "type": "source",
                       "version": "0.0.0.0"
                     },
                     {
                       "class": "io.confluent.connect.storage.tools.SchemaSourceConnector",
                       "type": "source",
                       "version": "7.4.1-ce"
                     },
                     {
                       "class": "io.confluent.kafka.connect.datagen.DatagenConnector",
                       "type": "source",
                       "version": "null"
                     },
                     {
                       "class": "io.confluent.salesforce.SalesforceCdcSourceConnector",
                       "type": "source",
                       "version": "2.0.1"
                     },
                     {
                       "class": "io.confluent.salesforce.SalesforceCdcSourceConnector",
                       "type": "source",
                       "version": "2.0.13"
                     },
                     {
                       "class": "io.confluent.salesforce.SalesforcePlatformEventSourceConnector",
                       "type": "source",
                       "version": "2.0.1"
                     },
                     {
                       "class": "io.confluent.salesforce.SalesforcePlatformEventSourceConnector",
                       "type": "source",
                       "version": "2.0.13"
                     },
                     {
                       "class": "io.confluent.salesforce.SalesforcePushTopicSourceConnector",
                       "type": "source",
                       "version": "2.0.1"
                     },
                     {
                       "class": "io.confluent.salesforce.SalesforcePushTopicSourceConnector",
                       "type": "source",
                       "version": "2.0.13"
                     },
                     {
                       "class": "io.confluent.salesforce.SalesforceSourceConnector",
                       "type": "source",
                       "version": "2.0.1"
                     },
                     {
                       "class": "io.confluent.salesforce.SalesforceSourceConnector",
                       "type": "source",
                       "version": "2.0.13"
                     },
                     {
                       "class": "io.debezium.connector.db2.Db2Connector",
                       "type": "source",
                       "version": "1.5.0.Final"
                     },
                     {
                       "class": "io.debezium.connector.db2.Db2Connector",
                       "type": "source",
                       "version": "2.4.0.Final"
                     },
                     {
                       "class": "io.debezium.connector.postgresql.PostgresConnector",
                       "type": "source",
                       "version": "1.9.5.Final"
                     },
                     {
                       "class": "io.debezium.connector.postgresql.PostgresConnector",
                       "type": "source",
                       "version": "2.4.0.Final"
                     },
                     {
                       "class": "io.debezium.connector.sqlserver.SqlServerConnector",
                       "type": "source",
                       "version": "1.2.2.Final"
                     },
                     {
                       "class": "io.debezium.connector.sqlserver.SqlServerConnector",
                       "type": "source",
                       "version": "2.4.0.Final"
                     },
                     {
                       "class": "org.apache.kafka.connect.mirror.MirrorCheckpointConnector",
                       "type": "source",
                       "version": "7.4.1-ce"
                     },
                     {
                       "class": "org.apache.kafka.connect.mirror.MirrorHeartbeatConnector",
                       "type": "source",
                       "version": "7.4.1-ce"
                     },
                     {
                       "class": "org.apache.kafka.connect.mirror.MirrorSourceConnector",
                       "type": "source",
                       "version": "7.4.1-ce"
                     },
                     {
                       "class": "org.init.ohja.kafka.connect.odatav2.source.OData2SourceConnector",
                       "type": "source",
                       "version": "1.3.3-265-7fb99475"
                     },
                     {
                       "class": "org.init.ohja.kafka.connect.odp.source.ODPSourceConnector",
                       "type": "source",
                       "version": "1.3.3-109-d258410f"
                     }
                   ]
                """.trimIndent()
                call.respond(
                    HttpStatusCode.OK, json
                )
            }
        }




        route("/setup") {
            put {
// Perform deserialization
                val request = try {
                    call.receive<ConnectorsTasksAndInfo>()
                } catch (e: Exception) {
                    println("Deserialization error: ${e.message}")
                    null
                }

                //val request = call.receive<Map<String, ConnectorTasksAndInfo>>()
                if (request != null) {
                    request.connectors.forEach { taskAndInfo ->
                        connectors.update {
                            (it + (taskAndInfo.key to taskAndInfo.value))
                        }
                    }
                    //return success response
                    call.respond(
                        HttpStatusCode.OK
                    )
                } else {
                    call.respond(
                        HttpStatusCode.BadRequest
                    )

                }
            }
        }
        // give the curl command to populate the connector
        // curl -X POST -H "Content-Type: application/json" -d '{"name":"connector1","config":{"connector.class":"io.lenses.runner.connect.ProcessorConnector","tasks.max":"1","topics":"topic1","connector.id":"connector1","name":"connector1","connect.sql":"select * from topic1","connect.sql.schema":"{\"type\":\"record\",\"name\":\"myrecord\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"},{\"name\":\"f2\",\"type\":\"int\"}]}"}}' http://localhost:8083/connectors
        route("/connectors") {
            post {
                val request = call.receive<ConnectorCreateRequest>()
                val tasksMax: Int = request.config["tasks.max"]?.toInt() ?: 0

                connectors.update {
                    (it + (request.name to ConnectorTasksAndInfo(
                        ConnectorStatusResponse(
                            request.name, ConnectorInstanceStatus(ConnectStatus.RUNNING, "worker1"),
                            buildTasksDetailsList(tasksMax)
                        ),
                        ConnectorInfo(
                            request.name, request.config, buildTasksList(request.name, tasksMax)
                        )
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
                    call.respond(
                        HttpStatusCode.OK, state
                    )
                }
            }

            route("{connectorName}") {
                get {
                    val connectorName = call.parameters.getOrFail("connectorName")
                    val state = connectors.get()
                    if (state.containsKey(connectorName)) {
                        val response = ConnectorResponse(
                            connectorName,
                            state[connectorName]?.info?.config ?: emptyMap(),
                            state[connectorName]?.info?.tasks ?: emptyList()
                        )
                        call.respond(HttpStatusCode.OK, response)
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ConnectorError(clusterId, 404, ErrorMessage("Connector $connectorName not found"))
                        )
                    }
                }

                route("topics"){
                    get {
                        val connectorName = call.parameters.getOrFail("connectorName")
                        val topics: Map<String, ConnectorTopics> = mapOf("topics" to ConnectorTopics(listOf("topic1", "topic2")))
                        call.respond(HttpStatusCode.OK, topics)
                    }
                }

                route("tasks"){
                    get {
                        val connectorName = call.parameters.getOrFail("connectorName")
                        val state = connectors.get()
                        val connector = state[connectorName]
                        if (connector != null) {
                            val response = ConnectorTaskResponse(
                                ConnectorTask(connectorName, 0),
                                connector.info.config
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
                route("restart") {
                    post {
                        val connectorName = call.parameters.getOrFail("connectorName")
                        //val state = connectors.get()[connectorName]?.status ?: emptyList()
                        val state = connectors.get()
                        val connector = state.get(connectorName)
                        if (connector == null) {
                            call.respond(
                                HttpStatusCode.NotFound,
                                ConnectorError(clusterId, 404, ErrorMessage("Connector $connectorName not found"))
                            )
                        } else {
                            val response = connector.status.copy(
                                connector = connector.status.connector.copy(state = ConnectStatus.RESTARTING),
                                tasks = connector.status.tasks.map { it.copy(state = ConnectStatus.RESTARTING) }
                            )

                            connectors.update {
                                (it + (connectorName to ConnectorTasksAndInfo(
                                    response,
                                    state[connectorName]?.info ?: ConnectorInfo(connectorName, emptyMap(), emptyList())
                                )))
                            }

                            call.respond(
                                HttpStatusCode.OK, response
                            )
                        }
                    }
                }
                route("status") {
                    put {
                        val connectorName = call.parameters.getOrFail("connectorName")
                        val request = call.receive<List<TaskStatus>>()
                        val state = connectors.get()
                        val connector = state[connectorName]
                        if (connector != null) {
                            val response = ConnectorStatusResponse(
                                connectorName,
                                ConnectorInstanceStatus(ConnectStatus.RUNNING, ""),
                                request
                            )
                            val updated = connector.copy(status = response)
                            connectors.update {
                                (it + (connectorName to updated))
                            }

                            call.respond(HttpStatusCode.OK, response)
                        } else {
                            call.respond(
                                HttpStatusCode.NotFound,
                                ConnectorError(clusterId, 404, ErrorMessage("Connector $connectorName not found"))
                            )
                        }
                    }

                    get {
                        val connectorName = call.parameters.getOrFail("connectorName")
                        val state = connectors.get()
                        val connector = state[connectorName]
                        if (connector != null) {
                            val response = connector.status
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

                        val connector = connectors.get()[connectorName]
                        if (connector == null) {
                            call.respond(
                                HttpStatusCode.NotFound,
                                ConnectorError(clusterId, 404, ErrorMessage("Connector $connectorName not found"))
                            )
                        } else {
                            connectors.update {
                                (it + (connectorName to ConnectorTasksAndInfo(
                                    connector.status,
                                    ConnectorInfo(
                                        connectorName,
                                        request.config,
                                        buildTasksList(connectorName, tasksMax)
                                    )
                                )))
                            }

                            call.respond(
                                HttpStatusCode.OK,
                                ConnectorResponse(
                                    connectorName,
                                    request.config,
                                    buildTasksList(connectorName, tasksMax)
                                )
                            )
                        }
                    }

                    get {
                        val connectorName = call.parameters.getOrFail("connectorName")
                        val connectorConfig: Map<String, String> =
                            connectors.get()[connectorName]?.info?.config ?: emptyMap()

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