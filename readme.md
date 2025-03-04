## Google Cloud Managed Kakfa & Dataflow

### TL;DR

Pipelines
- Kafka data producer, 1 message per second
- Kafka data consumer, 10s sliding window, for every 1s log a sample message per window + count number of messages per window

For load tests, use this [data producer](https://github.com/cloudymoma/managedkafka-producer) to dump messages to Kafka

### Quickstart

Simply change parameters defined in this [`makefile`](https://github.com/cloudymoma/managedkafka-dataflow/blob/main/makefile#L3-L10), for example, `project id`, `kafak server`, `topic name` etc.

then `make df` will launch the dataflow job, or `make up` will update the current running job

### DAG

![](https://raw.githubusercontent.com/cloudymoma/managedkafka-dataflow/main/miscs/dag.png)
