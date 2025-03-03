pwd := $(shell pwd)
ipaddr := $(shell hostname -I | cut -d ' ' -f 1)
gcp_project := du-hast-mich
region := us-central1
workerType := e2-standard-2
workerZone := b
gcs_bucket = bindiego
job := bindiego-kafka
kafka_server := bootstrap.dingo-kafka.us-central1.managedkafka.du-hast-mich.cloud.goog:9092
kafka_topic := dingo-topic

lo:
	@mvn compile exec:java -Dexec.mainClass=bindiego.BindiegoKafka

df:
	@mvn -Pdataflow-runner compile exec:java \
		-Dexec.mainClass=bindiego.BindiegoKafka \
		-Dexec.cleanupDaemonThreads=false \
		-Dexec.args="--project=$(gcp_project) \
--streaming=true \
--enableStreamingEngine \
--autoscalingAlgorithm=THROUGHPUT_BASED \
--maxNumWorkers=20 \
--workerMachineType=$(workerType) \
--diskSizeGb=64 \
--numWorkers=3 \
--tempLocation=gs://$(gcs_bucket)/tmp/ \
--gcpTempLocation=gs://$(gcs_bucket)/tmp/gcp/ \
--stagingLocation=gs://$(gcs_bucket)/staging/ \
--runner=DataflowRunner \
--experiments=use_runner_v2 \
--defaultWorkerLogLevel=DEBUG \
--jobName=$(job) \
--kafkaBootstrapServers=$(kafka_server) \
--kafkaTopic=$(kafka_topic)"

up:
	@mvn -Pdataflow-runner compile exec:java \
		-Dexec.mainClass=bindiego.BindiegoKafka \
		-Dexec.cleanupDaemonThreads=false \
		-Dexec.args="--project=$(gcp_project) \
--streaming=true \
--enableStreamingEngine \
--autoscalingAlgorithm=THROUGHPUT_BASED \
--maxNumWorkers=20 \
--workerMachineType=$(workerType) \
--diskSizeGb=64 \
--numWorkers=3 \
--tempLocation=gs://$(gcs_bucket)/tmp/ \
--gcpTempLocation=gs://$(gcs_bucket)/tmp/gcp/ \
--stagingLocation=gs://$(gcs_bucket)/staging/ \
--runner=DataflowRunner \
--experiments=use_runner_v2 \
--defaultWorkerLogLevel=DEBUG \
--jobName=$(job) \
--update \
--kafkaBootstrapServers=$(kafka_server) \
--kafkaTopic=$(kafka_topic)"
