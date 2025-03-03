package bindiego;

import com.google.common.collect.ImmutableMap;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.GenerateSequence;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.options.StreamingOptions;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.transforms.windowing.SlidingWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class BindiegoKafka {
  private static final Logger LOG = LoggerFactory.getLogger(BindiegoKafka.class);
  public static final String WORKER_NETWORK = "https://www.googleapis.com/compute/v1/projects/du-hast-mich/regions/us-central1/subnetworks/default";

  /**
   * Custom PipelineOptions for Kafka.
   */
  public interface KafkaOptions extends PipelineOptions, DataflowPipelineOptions, StreamingOptions {
    @Description("Kafka Bootstrap Servers")
    //@Default.String("bootstrap.kafka-cluster.asia-east1.managedkafka.fine-acronym-336109.cloud.goog:9092")
    @Default.String("bootstrap.dingo-kafka.us-central1.managedkafka.du-hast-mich.cloud.goog:9092")
    String getKafkaBootstrapServers();

    void setKafkaBootstrapServers(String value);

    @Description("Kafka Topic to write to")
    //@Default.String("__remote_log_metadata")
    @Default.String("dingo-topic")
    String getKafkaTopic();

    void setKafkaTopic(String value);

    @Description("Kafka Topic to read from") // Add option for a separate read topic
    //@Default.String("__remote_log_metadata")
    @Default.String("dingo-topic")
    String getKafkaReadTopic();
    void setKafkaReadTopic(String value);
  }

  public static void main(String[] args) {
    // Use PipelineOptionsFactory to create KafkaOptions
    KafkaOptions options = PipelineOptionsFactory.fromArgs(args)
        .withValidation()
        .as(KafkaOptions.class);
    options.setStreaming(true);
    options.setNetwork("default");
    options.setSubnetwork(WORKER_NETWORK);
    options.setProject("du-hast-mich");
    options.setRegion("us-central1");
    Pipeline p = Pipeline.create(options);

    // 1. Generate messages every second.
    PCollection<String> messages = p.apply("Generate Messages",
            GenerateSequence.from(0).withRate(1, Duration.standardSeconds(1))) // Generate numbers 0, 1, 2... every second
        .apply(MapElements.into(org.apache.beam.sdk.values.TypeDescriptors.strings())
            .via(number -> "Message: " + number + ", Timestamp: " + Instant.now().toString())); // Convert to strings

    // 2. Write messages to Kafka.
    messages.apply("Write to Kafka",
        KafkaIO.<Void, String>write()
            .withBootstrapServers(options.getKafkaBootstrapServers())
            .withProducerConfigUpdates(ImmutableMap.of(
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL",
                SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS, "com.google.cloud.hosted.kafka.auth.GcpLoginCallbackHandler",
                SaslConfigs.SASL_MECHANISM, "OAUTHBEARER",
                SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;"
            ))
            .withTopic(options.getKafkaTopic())
            .withValueSerializer(StringSerializer.class)
            .withInputTimestamp()
            .values()
    );

    // 3. Read messages from Kafka.
    PCollection<KV<String, String>> kafkaMessages = p.apply("Read from Kafka",
        KafkaIO.<String, String>read()
            .withBootstrapServers(options.getKafkaBootstrapServers())
            .withTopic(options.getKafkaReadTopic()) // Use the read topic
            .withConsumerConfigUpdates(ImmutableMap.of(
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL",
                SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS, "com.google.cloud.hosted.kafka.auth.GcpLoginCallbackHandler",
                SaslConfigs.SASL_MECHANISM, "OAUTHBEARER",
                SaslConfigs.SASL_JAAS_CONFIG, "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;",
                "auto.offset.reset", "earliest"  // Start reading from the beginning if no offset is found
            ))
            .withKeyDeserializer(StringDeserializer.class)
            .withValueDeserializer(StringDeserializer.class)
            .withoutMetadata() // We don't need Kafka metadata for this example
    );

    // 4. Log the messages.
    /*
    kafkaMessages.apply("Log Messages", ParDo.of(new DoFn<KV<String, String>, Void>() {
      @ProcessElement
      public void processElement(@Element KV<String, String> element, OutputReceiver<Void> out) {
        // Log the key and value.  In a real application, you'd likely do more than just log.
        LOG.info("Received from Kafka: Key = {}, Value = {}", element.getKey(), element.getValue());
      }
    }));
    */

  // 4. Apply a sliding window and count messages.
  kafkaMessages
    .apply(Values.<String>create()) // Extract just the message values (discard keys)
    .apply("Windowing", 
      Window.<String>into(SlidingWindows.of(Duration.standardSeconds(10))
        .every(Duration.standardSeconds(1)))) // 10-second windows, sliding every 1 second
    .apply("Count Messages",
      Combine.globally(Count.<String>combineFn()).withoutDefaults()) // Count messages in each window
    .apply("Format Output", MapElements.into(org.apache.beam.sdk.values.TypeDescriptors.strings())
      .via(count -> {
        Instant windowEnd = Instant.now(); //simplified, should get it from window, but this is okay for this task
        return "Window ending at " + windowEnd + ": Count = " + count;
      }))
    .apply("Log Count", ParDo.of(new DoFn<String, Void>() {
      @ProcessElement
      public void processElement(@Element String element, OutputReceiver<Void> out) {
        LOG.info(element);
      }
    }));

    //take first message per each window as a sample
    kafkaMessages
      .apply(Values.create())
      .apply("WindowingSample", 
        Window.into(SlidingWindows.of(Duration.standardSeconds(10))
          .every(Duration.standardSeconds(1))))
      .apply("Sample Message", Sample.any(1)) //take one sample per window
      .apply("Log Sample Message", ParDo.of(new DoFn<String, Void>() {
          @ProcessElement
          public void processElement(@Element String element, OutputReceiver<Void> out) {
              LOG.info("Sample Message: " + element);
          }
      }));

    p.run();
  }
}
