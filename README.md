# Spring Boot Redis Streams [![CI](https://github.com/daggerok/event-driver-redis-streaming-spring-boot-app/actions/workflows/ci.yaml/badge.svg)](https://github.com/daggerok/event-driver-redis-streaming-spring-boot-app/actions/workflows/ci.yaml)
This repository contains event driven redis app uses redis streams

## Run redis in docker

```bash
docker run --rm --name redis -itp6379:6379 redis:6.2.6-alpine
```

## HTTPie

```bash
mvn spring-boot:start

http --stream :8080 &
http :8080 body=Hello
http :8080 body=World

mvn spring-boot:stop
```

## Manual

### Create a redis stream called my_stream

```bash
docker exec -it redis redis-cli
127.0.0.1:6379> XADD my_stream * value "Hello, World!"
# output: identity, which is message id:
"1637443677304-0"
```

### Check what identity we have in redis

```bash
127.0.0.1:6379> KEYS *
# output:
"my_stream"
```

### Check type of key my_stream

```bash
127.0.0.1:6379> TYPE my_stream
# output:
stream
```

### Consume that stream

```bash
127.0.0.1:6379> XREAD STREAMS my_stream 0
# output:
1) 1) "my_stream"
   2) 1) 1) "1637443677304-0"
         2) 1) "value"
            2) "Hello, World!"
```

### Continue read stream from a last message I get

```bash
127.0.0.1:6379> XREAD STREAMS my_stream 1637443677304-0
# output:
(nil)
```

### Wait by blocking for 5 seconds for a next value from a stream

```bash
127.0.0.1:6379> XREAD BLOCK 5000 STREAMS my_stream 1637443677304-0
# output:
(nil)
(5.09s)
```

## Cleanup

```bash
docker stop redis
```

## rtfm

* [YouTube: Event-Driven Java Applications with Redis 5.0 Streams](youtube.com/watch?v=Gmwh-tUr_1E)
* https://dev.to/azure/redis-streams-in-action-part-3-java-app-to-process-tweets-with-redis-streams-3n7n
* https://github.com/mp911de/redis-stream-demo/
* https://www.youtube.com/watch?v=Y4T55XMpP_Y
* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/2.6.0/maven-plugin/reference/html/)
* [Create an OCI image](https://docs.spring.io/spring-boot/docs/2.6.0/maven-plugin/reference/html/#build-image)
* [Coroutines section of the Spring Framework Documentation](https://docs.spring.io/spring/docs/5.3.13/spring-framework-reference/languages.html#coroutines)
* [Spring Data Reactive Redis](https://docs.spring.io/spring-boot/docs/2.6.0/reference/htmlsingle/#boot-features-redis)
* [Messaging with Redis](https://spring.io/guides/gs/messaging-redis/)
