# connect-mock

This is a dummy application that mocks the Kafka Connect API. 
Any state is currently hold in memory, so restarting the mock service will make all your mock-connectors to be lost.
In addition, it currently exposes 3 different ports that emulates a Connect cluster with 3 different workers.

## The Goal
The first goal os this project is to be able to test applications that needs to interact with Kafka Connect. Having this mocked service will allow those apps to test scenarios handling several docens of connectors.
After this first iteration, the idea is start adding some random failures on the artificially deployed connectors and workers, so we can emulate disaster scenarios for apps speaking to Connect.

## Usage

### Docker
There is also a Dockerfile that will allow you to build and run the application

```
docker build -t fperezp/mock-connect:0.6.0 .
```


### Helm
The project provides a Helm chart that can be used to deploy the mock service to Kubernetes

```
helm install -n fran-test mock-connect ./helm
```

Friendly reminder, if you want to deploy using Helm you'll need to push the docker image to a docker registry accessible from the Kubernetes cluster you want to deploy the mock-connect app into.


### Example

To create a new connector, you can use the following command:

```
curl -X POST -H "Content-Type: application/json" -d '{"name":"s3sink","config":{"connector.class":"io.lenses.connect.aws.s3.S3SinkConnector","tasks.max":"1","topics":"your-topic","s3.bucket.name":"your-bucket-name","s3.region":"your-s3-region","format.class":"io.lenses.connect.aws.s3.format.parquet.ParquetFormat","flush.size":"1000","name":"s3-sink"}}' http://localhost:18083/connectors | jq
```

To set the status of a connector, you can use the following command:

```
curl -X PUT -H "Content-Type: application/json" -d '[{"id":0,"state":"RUNNING","worker_id":"worker1","trace":"trace"},{"id":1,"state":"FAILED","worker_id":"worker1","trace":"trace"}]' http://localhost:18083/connectors/s3sink/status | jq
```

To get the status of a connector, you can use the following command:

```
curl -X GET http://localhost:18083/connectors/s3sink/status  | jq
```