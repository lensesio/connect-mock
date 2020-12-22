# connect-mock

This is a dummy application that mocks the Kafka Connect API. 
Any state is currently hold in memory, so restarting the mock service will make all your mock-connectors to be lost.
In addition, it currently exposes 3 different ports that emulates a Connect cluster with 3 different workers.

## The Goal
The first goal os this project is to be able to test applications that needs to interact with Kafka Connect. Having this mocked service will allow those apps to test scenarios handling several docens of connectors.
After this first iteration, the idea is start adding some random failures on the artifitially deployed connectors and workers, so we can emulate disaster scenarios for apps speaking to Connect.

## Usage

### Docker
There is also a Dockerfile that will allow you to build and run the application

```
docker build -t fperezp/mock-connect:0.5.0 .
```


### Helm
The project provides a Helm chart that can be used to deploy the mock service to Kubernetes

```
helm install -n fran-test mock-connect ./helm
```

Friendly reminder, if you want to deploy using Helm you'll need to push the docker image to a docker registry accessible from the Kubernetes cluster you want to deploy the mock-connect app into.
