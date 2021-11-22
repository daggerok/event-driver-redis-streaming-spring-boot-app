package daggerok

import io.lettuce.core.Consumer
import io.lettuce.core.RedisClient
import io.lettuce.core.XAddArgs
import io.lettuce.core.XGroupCreateArgs
import io.lettuce.core.XReadArgs
import io.lettuce.core.XReadArgs.StreamOffset
import java.util.ArrayList
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled
class SimpleTest {

    @Test
    fun `should produce`() {
        val redisClient = RedisClient.create("redis://127.0.0.1:6379")
        val statefulRedisConnection = redisClient.connect()
        val redisCommands = statefulRedisConnection.sync()
        val id = redisCommands.xadd("my_stream", mapOf("value" to "Hello, World!"))
        println("id: $id")
    }

    @Test
    fun `should consume`() {
        val redisClient = RedisClient.create("redis://127.0.0.1:6379")
        val statefulRedisConnection = redisClient.connect()
        val redisCommands = statefulRedisConnection.sync()
        val result = redisCommands.xread(StreamOffset.from("my_stream", "0"))
        println("result: $result")
        for (streamMessage in result) {
            println("id: ${streamMessage.id}: ${streamMessage.body}")
        }
    }

    @Test
    fun `should consume with consumer group`() {
        val redisClient = RedisClient.create("redis://127.0.0.1:6379")
        val statefulRedisConnection = redisClient.connect()
        val redisCommands = statefulRedisConnection.sync()
        val xInfoGroups = redisCommands.xinfoGroups("my_stream")
        // if (xInfoGroups.isEmpty() || xInfoGroups.first().javaClass.isAssignableFrom(ArrayList::class.java) || xInfoGroups.first() as List) // ../
        // val xGroupCreate = redisCommands.xgroupCreate(StreamOffset.latest("my_stream"), "my_group", XGroupCreateArgs().mkstream(true))
        // println("xGroupCreate: $xGroupCreate")
        val result = redisCommands.xreadgroup(Consumer.from("my_group", "my_stream"), StreamOffset.lastConsumed("my_stream"))
        println("result: $result")
        for (streamMessage in result) {
            println("id: ${streamMessage.id}: ${streamMessage.body}")
        }
    }
}
