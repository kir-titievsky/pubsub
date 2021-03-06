### Introduction

This load test framework, known as Flic (Framework of
load & integration for Cloud Pub/Sub), for Cloud Pub/Sub
is a tool targeted for developers
and companies who use [Kafka](http://kafka.apache.org).
The goal of this framework is twofold:

1.  Provide users with a tool that allows them to see how Cloud Pub/Sub performs
    under various conditions.

2.  Provide a tool for integration testing the CloudPubSubConnector (link it),
    where the purpose is to show that the CloudPubSubConnector works and is a
    viable tool that can transfer your messages from Kafka to Cloud Pub/Sub and
    vice versa.

### Building

These instructions assume you are using [Maven](https://maven.apache.org/).

1.  Clone the repository, ensuring to do so recursively to pick up submodules:

    `git clone --recursive ADD LINK ONCE REPO LOCATION IS DETERMINED`

2.  Make the jar that contains the connector:

    `mvn package`

The resulting jar is at target/flic.jar.

### Pre-Running Steps

1.  Regardless of whether you are running on Google Cloud Platform or not, you
    need to create a project and create a service key that allows you access to
    the Cloud Pub/Sub API's and default quotas.

2.  Create project on Google Cloud Platform. By default, this project will have
    multiple service accounts associated with it (see "IAM & Admin" within GCP
    console). Within this section, find the tab for "Service Accounts". Create a
    new service account and make sure to select "Furnish a new private key".
    Doing this will create the service account and download a private key file
    to your local machine.

3.  Go to the "IAM" tab, find the service account you just created and click on
    the dropdown menu named "Role(s)". Under the "Pub/Sub" submenu, select
    "Pub/Sub Admin". Finally, the key file that was downloaded to your machine
    needs to be placed on the machine running the framework. An environment
    variable named GOOGLE_APPLICATION_CREDENTIALS must point to this file. (Tip:
    export this environment variable as part of your shell startup file).

    `export GOOGLE_APPLICATION_CREDENTIALS=/path/to/key/file`

### Important Notes

It is important to first point out that we are providing a framework for load
testing Cloud Pub/Sub. While it is possible to publish and consume messages to
Kafka from this framework, the client code used to do so has not yet been
optimized to show off the things Kafka does well. On the other hand, the client
code for Cloud Pub/Sub is optimized. A list of the optimizations and their
benefits are as follows.

*   Round Robin Request Dispatching - Distributes request load across multiple
    clients to improve throughput.

*   Callback Thread Pools - Improves latency by distributing async callback
    computation among threads.

*   Batching - Improves publisher throughput.

*   Rate Limiting - Decreases pub-to-ack latency on the publisher side.

In summary, purpose of the Kafka client code is not to be a comparison for the
Cloud Pub/Sub client code but rather code that facilitates the portion of the
framework that deals with the integration tests for the CloudPubSubConnector.
(See section labeled "Integration Component"). If you would like to compare the
performance of Cloud Pub/Sub and Kafka under the same scenarios, then either run
your own Kafka load tests, or modify the existing Kafka client code in the
framework to make it as optimized as you would like.

### Quickstart

1.  The jar file can be executed with numerous commands and options specified
    from the command line. In a single invocation, the framework allows you to
    use Kafka or Cloud Pub/Sub as the message service, and to publish or consume
    messages from this service. The following command runs a load test for Cloud
    Pub/Sub using the provided script run.py. Make sure to fill in the required
    field in the script before execution. For example, you must replace '[YOUR
    TOPIC HERE]' with an existing topic.

    `python -c 'import run; run.cps_pub_load_test()'`

2.  To get a list of all available commands, options and defaults, run the
    following.

    `./run.py --help`

### Load Testing Component

1.  Imagine you wanted to run a small and simple load test that published
    messages to Cloud Pub/Sub. Below is an example of an invocation of the
    framework that would do this:

    `./run.py --num_messages=1000 --topics=mytopic cps --project=myproject`

2.  The output for this test would look something like this:

    `INFO - Creating a task which publishes to CPS.`

    `INFO - Progress:Asynchronously processed 100 messages`

    `INFO - Progress: Asynchronously processed 1000 messages`

    `INFO - Pub-to-Ack for 1000 messages: (min, max, avg, 50%, 95%, 99%) =
    [STATS HERE]`

    `INFO - Average Throughput: [STATS HERE]`

    `INFO - Done!`

3.  Now imagine you want to run a much more involved load test where you use
    different command line arguments to alter how the Cloud Pub/Sub performace.
    For example, let us say you want to publish 1 million messages of size 1 KB
    and you want the messages to be sent in batches of 1000. Also, you would
    like for there to be 5 round robin clients and 3 threads in the callback
    thread pool. Finally, you would like there to be a limit of 100 requests per
    second. The following command does this for you:

    `./run.py --num_messages=1000 --message_size=1024 --topics=mytopic cps
    --project=myproject --batch_size=1000 --num_clients=5 --response_threads=3
    --rate_limit=100`

4.  For our last example, imagine you want to both publish and consume messages
    from Cloud Pub/Sub. One feature of the Cloud Pub/Sub client code which
    consumes messages is that it indicates to you when to start publishing. This
    is necessary because the subscriptions need to be made before messages start
    flowing into a topic. The steps below show how to run this test:

    `./run.py --publish false --num_messages=1000 --topics=mytopic cps
    --project=myproject (Run this in a different shell)`.

    `./run.py --num_messages=1000 --topics=mytopic cps --project=myproject`.

5.  The output for this test would look something like this: (We omit the output
    from the publisher, see step #2 for how that looks).

    `INFO - Creating a task which consumes from CPS`

    `INFO - Start publishing...`

    `INFO - Progress: Asynchronously processed 100 messages`

    `INFO - Progress: Asynchronously processed 1000 messages`

    `INFO - End-to-End for 1000 messages: (min, max, avg, 50%, 95%, 99%) =
    [STATS HERE]`

    `INFO - Average Throughput: [STATS HERE]`

    `INFO - Done!`

### Integration Component

It is easy to test out the CloudPubSubConnector and explore its functionality by
creating some simple "integration" tests. The following steps would perform a
simple integration test for the sink connector.

1.  Create a topic on Kafka. Run two invocations of the framework, one that
    consumes messages from Kafka and one tha publishes. Be sure to turn on data
    dumps (This dumps consumed message data into a directory "data").

    `./run.py --publish false --dump_data --topics=mytopic kafka
    --broker=localhost:9092`

    `./run.py --topics=mytopic kafka --broker=localhost:9092`

2.  Once those two are complete, create a topic on Cloud Pub/Sub and run an
    invocation of the framewok that consumes from CPS. Turn data dumps on here
    also. Once you get the "Start publishing" signal. start the connector
    (Ensure your connector is properly configured) `

    `./run.py --publish false --dump_data --topics=mytopic cps
    --project=myproject`

3.  Use the "compare" command to verify that the consumed messages match. The
    data which you dumped previously will be sitting in data/CPS and data/KAFKA.

    `./run.py compare --file1=CPS --file2=KAFKA`

Similar steps can be taken to perform an integration test for the source
connector.

### Common Errors

1.  If an invocation of the framework publishes or consumes from Kafka, but you
    do not have a Kafka cluster running, you will see the following error:

    `Failed to update metadata after (some amount) ms.`

2.  Similarly, if an invocation of the framework publishes or consumes from
    Cloud Pub/Sub but you do not have a Google Cloud Platform project or the
    topic you specified does not exist, you will see the following error:

    `RESOURCE_NOT_FOUND`

3.  If you failed to properly set up a service account key and point the
    appropriate environment variable to the key file, you will get an error that
    looks like the following:

    `Request had insufficient authentication scopes`

4.  There are times when gRPC (the RPC framework that powers the Cloud Pub Sub
    client code), runs out of memory. You will see the following error(s) if
    this happens:

    `java.lang.OutOfMemoryError: Direct buffer memory`
    `java.lang.OutOfMemoryError: Java heap space` `java.lang.OutOfMemoryError:
    GC overhead limit reached`

    To fix this, simply give the JVM more memory to work with.
