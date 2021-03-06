---
id: io-kinesis-sink
title: Kinesis sink connector
sidebar_label: Kinesis sink connector
---

The Kinesis sink connector pulls data from Pulsar and persists data into Amazon Kinesis.

## Configuration

The configuration of the Kinesis sink connector has the following parameters.

### Parameter

| Name | Type|Required | Default | Description
|------|----------|----------|---------|-------------|
`messageFormat`|MessageFormat|true|ONLY_RAW_PAYLOAD|Message format in which Kinesis sink converts Pulsar messages and publishes to Kinesis streams.<br/><br/>Below are the available options:<br/><br/><li>`ONLY_RAW_PAYLOAD`: Kinesis sink directly publishes Pulsar message payload as a message into the configured Kinesis stream. <br/><br/><li>`FULL_MESSAGE_IN_JSON`: Kinesis sink creates a JSON payload with Pulsar message payload, properties and encryptionCtx, and publishes JSON payload into the configured Kinesis stream.<br/><br/><li>`FULL_MESSAGE_IN_FB`: Kinesis sink creates a flatbuffer serialized payload with Pulsar message payload, properties and encryptionCtx, and publishes flatbuffer payload into the configured Kinesis stream.
`retainOrdering`|boolean|false|false|Whether Pulsar connectors to retain ordering when moving messages from Pulsar to Kinesis or not.
`awsEndpoint`|String|false|" " (empty string)|The Kinesis end-point URL, which can be found at [here](https://docs.aws.amazon.com/general/latest/gr/rande.html).
`awsRegion`|String|false|" " (empty string)|The AWS region. <br/><br/>**Example**<br/> us-west-1, us-west-2
`awsKinesisStreamName`|String|true|" " (empty string)|The Kinesis stream name.
`awsCredentialPluginName`|String|false|" " (empty string)|The fully-qualified class name of implementation of {@inject: github:`AwsCredentialProviderPlugin`:/pulsar-io/kinesis/src/main/java/org/apache/pulsar/io/kinesis/AwsCredentialProviderPlugin.java}. <br/><br/>It is a factory class which creates an AWSCredentialsProvider that is used by Kinesis sink. <br/><br/>If it is empty, the Kinesis sink creates a default AWSCredentialsProvider which accepts json-map of credentials in `awsCredentialPluginParam`.
`awsCredentialPluginParam`|String |false|" " (empty string)|The JSON parameter to initialize `awsCredentialsProviderPlugin`.

### Example

Before using the Kinesis sink connector, you need to create a configuration file through one of the following methods.

* JSON

    ```json
    {
        "awsEndpoint": "https://some.endpoint.aws",
        "awsRegion": "us-east-1",
        "awsKinesisStreamName": "my-stream",
        "awsCredentialPluginParam": "{\"accessKey\":\"myKey\",\"secretKey\":\"my-Secret\"}",
        "messageFormat": "ONLY_RAW_PAYLOAD",
        "retainOrdering": "true"
    }
    ```

* YAML

    ```yaml
    configs:
        awsEndpoint: "https://some.endpoint.aws"
        awsRegion: "us-east-1"
        awsKinesisStreamName: "my-stream"
        awsCredentialPluginParam: "{\"accessKey\":\"myKey\",\"secretKey\":\"my-Secret\"}"
        messageFormat: "ONLY_RAW_PAYLOAD"
        retainOrdering: "true"
    ```

### Built-in `AwsCredentialProviderPlugin` plugins

#### `org.apache.pulsar.io.kinesis.AwsDefaultProviderChainPlugin`
This plugin takes no configuration, it uses the default AWS provider chain. See the [AWS documentation](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html#credentials-default) for more details

#### `org.apache.pulsar.io.kinesis.STSAssumeRoleProviderPlugin`
This plugin takes a configuration (via the `awsCredentialPluginParam`) that describes a role to assume when running the KCL.

This configuration takes the form of a small json document like:
```Json
{"roleArn": "arn...", "roleSessionName": "name"}
```
