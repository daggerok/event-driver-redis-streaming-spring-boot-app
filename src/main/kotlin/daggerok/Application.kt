package daggerok

import io.lettuce.core.RedisClient
import io.lettuce.core.XGroupCreateArgs
import io.lettuce.core.XReadArgs.StreamOffset
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import mu.KLogging
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import org.springframework.data.redis.stream.Subscription
import org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.core.publisher.Sinks.Many
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@Configuration
@EnableConfigurationProperties(RedisStreamingProps::class)
class RedisStreamingPropsConfig

@ConstructorBinding
@ConfigurationProperties("redis-streaming")
data class RedisStreamingProps(
    val host: String,
    val port: Int,
    val streamName: String,
    val groupName: String,
)

@Configuration
class RedisClientConfig {

    @Bean
    fun redisClient(props: RedisStreamingProps): RedisClient =
        RedisClient.create("redis://${props.host}:${props.port}")

    @Bean(destroyMethod = "close")
    fun statefulRedisConnection(redisClient: RedisClient): StatefulRedisConnection<String, String> =
        redisClient.connect()

    @Bean
    fun redisCommands(statefulRedisConnection: StatefulRedisConnection<String, String>): RedisCommands<String, String> =
        statefulRedisConnection.sync()

    @Bean
    fun createConsumerGroupIf(redisCommands: RedisCommands<String, String>, props: RedisStreamingProps) =
        InitializingBean {
            val streamOffset = StreamOffset.latest(props.streamName)
            val makeStream = XGroupCreateArgs().mkstream(true)
            val execution = runCatching {
                redisCommands.xgroupCreate(streamOffset, props.groupName, makeStream)
            }
            if (execution.isFailure) {
                val e = execution.exceptionOrNull()
                logger.warn/*(e)*/ { "Cannot created consumer group ${props.groupName}. ${e?.message}" }
                return@InitializingBean
            }
            val result = execution.getOrThrow()
            logger.info { "Group created: $result, stream: ${props.streamName} are created" }
        }

    private companion object : KLogging()
}

data class Message(val id: String = "", val body: String)

@Configuration
class EventProcessingConfig {

    @Bean
    fun messageEventProcessor() =
        Sinks.many()
            .multicast()
            .directBestEffort<Message>()

    @Bean
    fun messageEventPublished(messageEventProcessor: Sinks.Many<Message>) =
        java.util.function.Consumer<Message> {
            messageEventProcessor.tryEmitNext(it)
        }

    @Bean(destroyMethod = "dispose")
    fun messageEventStreamScheduler() =
        Schedulers.newSingle("messageEventStreamScheduler")

    @Bean
    fun messageEventSubscription(messageEventProcessor: Many<Message>, messageEventStreamScheduler: Scheduler) =
        messageEventProcessor
            .asFlux()
            .publishOn(messageEventStreamScheduler)
            .subscribeOn(messageEventStreamScheduler)
            .onBackpressureBuffer(1025 * 16)
}

@Configuration
class RedisStreamListener {

    @Bean
    fun appRedisConnectionFactory(props: RedisStreamingProps) =
        LettuceConnectionFactory.createRedisConfiguration("redis://${props.host}:${props.port}")

    @Bean(initMethod = "start", destroyMethod = "stop")
    fun appStreamMessageListenerContainer(appRedisConnectionFactory: RedisConnectionFactory): StreamMessageListenerContainer<String, MapRecord<String, String, String>> =
        StreamMessageListenerContainer.create(appRedisConnectionFactory)

    @Bean(destroyMethod = "cancel")
    fun subscription(
        props: RedisStreamingProps,
        messageEventPublished: java.util.function.Consumer<Message>,
        appStreamMessageListenerContainer: StreamMessageListenerContainer<String, MapRecord<String, String, String>>,
    ): Subscription =
    // appStreamMessageListenerContainer.receive( // consume always all from beginning on start:
    //     org.springframework.data.redis.connection.stream.StreamOffset.fromStart(props.streamName)
    // ) {
    //     logger.info { "Handle stream message: $it" }
        // }
        appStreamMessageListenerContainer.receive(
            // consume only new messages from start
            org.springframework.data.redis.connection.stream.StreamOffset.create(
                props.streamName,
                ReadOffset.lastConsumed()
            ),
        ) {
            logger.info { "Handle stream message: $it" }
            val message = Message(id = it.id.value, body = it.value["value"] ?: "fuck it...")
            logger.info { "Handle message: $message" }
            messageEventPublished.accept(message)
        }

    private companion object : KLogging()
}

@RestController
class MessagesResource(
    private val props: RedisStreamingProps,
    private val redisCommands: RedisCommands<String, String>,
    private val messageEventSubscription: Flux<Message>,
) {

    /**
     * http :8080 body=Hey!
     */
    @PostMapping
    fun sendMessage(@RequestBody message: Message) =
        redisCommands.xadd(props.streamName, mapOf("value" to message.body))
            .let { message.copy(id = it) }

    /**
     * http :8080
     */
    @GetMapping(produces = [TEXT_EVENT_STREAM_VALUE])
    fun getAllMessages() =
        messageEventSubscription
}
