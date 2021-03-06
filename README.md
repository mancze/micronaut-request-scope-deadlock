## Steps to reproduce

```bash
./gradlew test

> Task :test

BeanInitializationDeadlockSpec > deadlockTest() FAILED
    org.opentest4j.AssertionFailedError at BeanInitializationDeadlockSpec.java:22
        Caused by: org.junit.jupiter.api.AssertTimeout$ExecutionTimeoutException at BeanInitializationDeadlockSpec.java:23
<===========--> 85% EXECUTING [18s]
> :test > 1 test completed, 1 failed
> :test > Executing test com.example...controller.BeanInitializationDeadlockSpec
```

If the repro-steps were successful the process will hang indefinitiely due to deadlock situation.

## Investigation

Thread dump of two threads waiting for each other is below.

Locks in play:

- `RequestCustomScope`'s `ReentrantReadWriteLock` (in its base class `AbstractConcurrentCustomScope`)
- `singletonObjects` monitor (by using `synchronized` keyword)

What is happening is that one thread (for http request) acquires write lock of `RequestCustomScope`. While initialization this request-scoped bean a singleton bean dependency is needed (need to enter `singletonObjects` synchronized block). But before this singleton block is entered, another thread acquires `singletonObjects` monitor.

This other thread is in the stage when it is in a `singletonObjects` synchronized block to create some bean. In the case above the bean to initialize itself is not a singleton (but prototype), but is produced by a factory (which is singleton - not sure if it plays a role). The key point is here that thread is in `singletonObjects` synchronized section. The factory itself however needs a request-scope dependency and therefore to obtain read lock of `RequestCustomScope`'s `ReentrantReadWriteLock`.

## Thread dump

*(relevant threads only)*

```text
"default-nioEventLoopGroup-1-4@5383" prio=5 tid=0x25 nid=NA waiting
  java.lang.Thread.State: WAITING
	 blocks Test worker@1
	 blocks default-nioEventLoopGroup-1-3@5382
	  at jdk.internal.misc.Unsafe.park(Unsafe.java:-1)
	  at java.util.concurrent.locks.LockSupport.park(LockSupport.java:211)
	  at java.util.concurrent.locks.AbstractQueuedSynchronizer.acquire(AbstractQueuedSynchronizer.java:715)
	  at java.util.concurrent.locks.AbstractQueuedSynchronizer.acquireShared(AbstractQueuedSynchronizer.java:1027)
	  at java.util.concurrent.locks.ReentrantReadWriteLock$ReadLock.lock(ReentrantReadWriteLock.java:738)
	  at io.micronaut.context.scope.AbstractConcurrentCustomScope.getOrCreate(AbstractConcurrentCustomScope.java:125)
	  at io.micronaut.context.DefaultBeanContext.getScopedBeanForDefinition(DefaultBeanContext.java:2889)
	  at io.micronaut.context.DefaultBeanContext.getBeanForDefinition(DefaultBeanContext.java:2822)
	  at io.micronaut.context.DefaultBeanContext.getProxyTargetBean(DefaultBeanContext.java:1423)
	  at com.example.micronaut.sandbox.controller.$RequestScopedBeanWaitingForReadLock$Definition$Intercepted.interceptedTarget(Unknown Source:-1)
	  at com.example.micronaut.sandbox.controller.$RequestScopedBeanWaitingForReadLock$Definition$Intercepted.getValue(Unknown Source:-1)
	  at com.example.micronaut.sandbox.controller.PrototypeFactory.create(PrototypeFactory.java:18)
	  at com.example.micronaut.sandbox.controller.$PrototypeFactory$Create0$Definition.build(Unknown Source:-1)
	  at io.micronaut.context.DefaultBeanContext.doCreateBean(DefaultBeanContext.java:2336)
	  at io.micronaut.context.DefaultBeanContext.getScopedBeanForDefinition(DefaultBeanContext.java:2922)
	  at io.micronaut.context.DefaultBeanContext.getBeanForDefinition(DefaultBeanContext.java:2822)
	  at io.micronaut.context.DefaultBeanContext.getBeanInternal(DefaultBeanContext.java:2782)
	  - locked <0x15d0> (a java.util.concurrent.ConcurrentHashMap)
	  at io.micronaut.context.DefaultBeanContext.getBean(DefaultBeanContext.java:1638)
	  at io.micronaut.inject.provider.JakartaProviderBeanDefinition.lambda$buildProvider$0(JakartaProviderBeanDefinition.java:65)
	  at io.micronaut.inject.provider.JakartaProviderBeanDefinition$$Lambda$688/0x00000008010ce710.get(Unknown Source:-1)
	  at com.example.micronaut.sandbox.controller.BarController.acquiresSingletonMonitorAndWaitsForRLock(BarController.java:26)
	  at com.example.micronaut.sandbox.controller.$BarController$Definition$Exec.dispatch(Unknown Source:-1)
	  at io.micronaut.context.AbstractExecutableMethodsDefinition$DispatchedExecutableMethod.invoke(AbstractExecutableMethodsDefinition.java:351)
	  at io.micronaut.context.DefaultBeanContext$4.invoke(DefaultBeanContext.java:583)
	  at io.micronaut.web.router.AbstractRouteMatch.execute(AbstractRouteMatch.java:246)
	  at io.micronaut.web.router.RouteMatch.execute(RouteMatch.java:111)
	  at io.micronaut.http.server.RouteExecutor$$Lambda$687/0x00000008010ce4f0.get(Unknown Source:-1)
	  at io.micronaut.http.context.ServerRequestContext.with(ServerRequestContext.java:103)
	  at io.micronaut.http.server.RouteExecutor.lambda$executeRoute$14(RouteExecutor.java:656)
	  at io.micronaut.http.server.RouteExecutor$$Lambda$686/0x00000008010ce2b0.apply(Unknown Source:-1)
	  at reactor.core.publisher.FluxDeferContextual.subscribe(FluxDeferContextual.java:49)
	  at reactor.core.publisher.Flux.subscribe(Flux.java:8468)
	  at reactor.core.publisher.FluxFlatMap$FlatMapMain.onNext(FluxFlatMap.java:426)
	  at io.micronaut.reactive.reactor.instrument.ReactorSubscriber.onNext(ReactorSubscriber.java:57)
	  at reactor.core.publisher.Operators$ScalarSubscription.request(Operators.java:2398)
	  at reactor.core.publisher.FluxFlatMap$FlatMapMain.onSubscribe(FluxFlatMap.java:371)
	  at io.micronaut.reactive.reactor.instrument.ReactorSubscriber.onSubscribe(ReactorSubscriber.java:50)
	  at reactor.core.publisher.FluxJust.subscribe(FluxJust.java:68)
	  at reactor.core.publisher.Flux.subscribe(Flux.java:8468)
	  at io.micronaut.http.server.netty.RoutingInBoundHandler.handleRouteMatch(RoutingInBoundHandler.java:571)
	  at io.micronaut.http.server.netty.RoutingInBoundHandler.channelRead0(RoutingInBoundHandler.java:434)
	  at io.micronaut.http.server.netty.RoutingInBoundHandler.channelRead0(RoutingInBoundHandler.java:141)
	  at io.netty.channel.SimpleChannelInboundHandler.channelRead(SimpleChannelInboundHandler.java:99)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.channel.SimpleChannelInboundHandler.channelRead(SimpleChannelInboundHandler.java:102)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java:103)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.micronaut.http.netty.stream.HttpStreamsHandler.channelRead(HttpStreamsHandler.java:223)
	  at io.micronaut.http.netty.stream.HttpStreamsServerHandler.channelRead(HttpStreamsServerHandler.java:123)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.channel.ChannelInboundHandlerAdapter.channelRead(ChannelInboundHandlerAdapter.java:93)
	  at io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler.channelRead(WebSocketServerExtensionHandler.java:99)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java:103)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java:103)
	  at io.netty.handler.codec.MessageToMessageCodec.channelRead(MessageToMessageCodec.java:111)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.channel.ChannelInboundHandlerAdapter.channelRead(ChannelInboundHandlerAdapter.java:93)
	  at io.netty.handler.codec.http.HttpServerKeepAliveHandler.channelRead(HttpServerKeepAliveHandler.java:64)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.handler.flow.FlowControlHandler.dequeue(FlowControlHandler.java:200)
	  at io.netty.handler.flow.FlowControlHandler.channelRead(FlowControlHandler.java:162)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.channel.CombinedChannelDuplexHandler$DelegatingChannelHandlerContext.fireChannelRead(CombinedChannelDuplexHandler.java:436)
	  at io.netty.handler.codec.ByteToMessageDecoder.fireChannelRead(ByteToMessageDecoder.java:324)
	  at io.netty.handler.codec.ByteToMessageDecoder.channelRead(ByteToMessageDecoder.java:296)
	  at io.netty.channel.CombinedChannelDuplexHandler.channelRead(CombinedChannelDuplexHandler.java:251)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.handler.timeout.IdleStateHandler.channelRead(IdleStateHandler.java:286)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.channel.DefaultChannelPipeline$HeadContext.channelRead(DefaultChannelPipeline.java:1410)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.DefaultChannelPipeline.fireChannelRead(DefaultChannelPipeline.java:919)
	  at io.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe.read(AbstractNioByteChannel.java:166)
	  at io.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:722)
	  at io.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:658)
	  at io.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:584)
	  at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:496)
	  at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:986)
	  at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
	  at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	  at java.lang.Thread.run(Thread.java:833)

"default-nioEventLoopGroup-1-3@5382" prio=5 tid=0x24 nid=NA waiting for monitor entry
  java.lang.Thread.State: BLOCKED
	 waiting for default-nioEventLoopGroup-1-4@5383 to release lock on <0x15d0> (a java.util.concurrent.ConcurrentHashMap)
	  at io.micronaut.context.DefaultBeanContext.getBeanInternal(DefaultBeanContext.java:2750)
	  at io.micronaut.context.DefaultBeanContext.getBean(DefaultBeanContext.java:1638)
	  at io.micronaut.context.AbstractInitializableBeanDefinition.resolveBean(AbstractInitializableBeanDefinition.java:1933)
	  at io.micronaut.context.AbstractInitializableBeanDefinition.getBeanForConstructorArgument(AbstractInitializableBeanDefinition.java:1189)
	  at com.example.micronaut.sandbox.controller.$RequestScopeBean$Definition.build(Unknown Source:-1)
	  at io.micronaut.context.DefaultBeanContext.doCreateBean(DefaultBeanContext.java:2336)
	  at io.micronaut.context.DefaultBeanContext.doCreateBean(DefaultBeanContext.java:1128)
	  at io.micronaut.context.DefaultBeanContext$6.create(DefaultBeanContext.java:2906)
	  at io.micronaut.context.scope.AbstractConcurrentCustomScope.doCreate(AbstractConcurrentCustomScope.java:167)
	  at io.micronaut.runtime.http.scope.RequestCustomScope.doCreate(RequestCustomScope.java:86)
	  at io.micronaut.context.scope.AbstractConcurrentCustomScope.getOrCreate(AbstractConcurrentCustomScope.java:143)
	  at io.micronaut.context.DefaultBeanContext.getScopedBeanForDefinition(DefaultBeanContext.java:2889)
	  at io.micronaut.context.DefaultBeanContext.getBeanForDefinition(DefaultBeanContext.java:2822)
	  at io.micronaut.context.DefaultBeanContext.getProxyTargetBean(DefaultBeanContext.java:1423)
	  at com.example.micronaut.sandbox.controller.$RequestScopeBean$Definition$Intercepted.interceptedTarget(Unknown Source:-1)
	  at com.example.micronaut.sandbox.controller.$RequestScopeBean$Definition$Intercepted.getValue(Unknown Source:-1)
	  at com.example.micronaut.sandbox.controller.FooController.acquiresWLockAndWaitsForSingletonMonitor(FooController.java:25)
	  at com.example.micronaut.sandbox.controller.$FooController$Definition$Exec.dispatch(Unknown Source:-1)
	  at io.micronaut.context.AbstractExecutableMethodsDefinition$DispatchedExecutableMethod.invoke(AbstractExecutableMethodsDefinition.java:351)
	  at io.micronaut.context.DefaultBeanContext$4.invoke(DefaultBeanContext.java:583)
	  at io.micronaut.web.router.AbstractRouteMatch.execute(AbstractRouteMatch.java:246)
	  at io.micronaut.web.router.RouteMatch.execute(RouteMatch.java:111)
	  at io.micronaut.http.server.RouteExecutor$$Lambda$687/0x00000008010ce4f0.get(Unknown Source:-1)
	  at io.micronaut.http.context.ServerRequestContext.with(ServerRequestContext.java:103)
	  at io.micronaut.http.server.RouteExecutor.lambda$executeRoute$14(RouteExecutor.java:656)
	  at io.micronaut.http.server.RouteExecutor$$Lambda$686/0x00000008010ce2b0.apply(Unknown Source:-1)
	  at reactor.core.publisher.FluxDeferContextual.subscribe(FluxDeferContextual.java:49)
	  at reactor.core.publisher.Flux.subscribe(Flux.java:8468)
	  at reactor.core.publisher.FluxFlatMap$FlatMapMain.onNext(FluxFlatMap.java:426)
	  at io.micronaut.reactive.reactor.instrument.ReactorSubscriber.onNext(ReactorSubscriber.java:57)
	  at reactor.core.publisher.Operators$ScalarSubscription.request(Operators.java:2398)
	  at reactor.core.publisher.FluxFlatMap$FlatMapMain.onSubscribe(FluxFlatMap.java:371)
	  at io.micronaut.reactive.reactor.instrument.ReactorSubscriber.onSubscribe(ReactorSubscriber.java:50)
	  at reactor.core.publisher.FluxJust.subscribe(FluxJust.java:68)
	  at reactor.core.publisher.Flux.subscribe(Flux.java:8468)
	  at io.micronaut.http.server.netty.RoutingInBoundHandler.handleRouteMatch(RoutingInBoundHandler.java:571)
	  at io.micronaut.http.server.netty.RoutingInBoundHandler.channelRead0(RoutingInBoundHandler.java:434)
	  at io.micronaut.http.server.netty.RoutingInBoundHandler.channelRead0(RoutingInBoundHandler.java:141)
	  at io.netty.channel.SimpleChannelInboundHandler.channelRead(SimpleChannelInboundHandler.java:99)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.channel.SimpleChannelInboundHandler.channelRead(SimpleChannelInboundHandler.java:102)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java:103)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.micronaut.http.netty.stream.HttpStreamsHandler.channelRead(HttpStreamsHandler.java:223)
	  at io.micronaut.http.netty.stream.HttpStreamsServerHandler.channelRead(HttpStreamsServerHandler.java:123)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.channel.ChannelInboundHandlerAdapter.channelRead(ChannelInboundHandlerAdapter.java:93)
	  at io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler.channelRead(WebSocketServerExtensionHandler.java:99)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java:103)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java:103)
	  at io.netty.handler.codec.MessageToMessageCodec.channelRead(MessageToMessageCodec.java:111)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.channel.ChannelInboundHandlerAdapter.channelRead(ChannelInboundHandlerAdapter.java:93)
	  at io.netty.handler.codec.http.HttpServerKeepAliveHandler.channelRead(HttpServerKeepAliveHandler.java:64)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.handler.flow.FlowControlHandler.dequeue(FlowControlHandler.java:200)
	  at io.netty.handler.flow.FlowControlHandler.channelRead(FlowControlHandler.java:162)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.channel.CombinedChannelDuplexHandler$DelegatingChannelHandlerContext.fireChannelRead(CombinedChannelDuplexHandler.java:436)
	  at io.netty.handler.codec.ByteToMessageDecoder.fireChannelRead(ByteToMessageDecoder.java:324)
	  at io.netty.handler.codec.ByteToMessageDecoder.channelRead(ByteToMessageDecoder.java:296)
	  at io.netty.channel.CombinedChannelDuplexHandler.channelRead(CombinedChannelDuplexHandler.java:251)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.handler.timeout.IdleStateHandler.channelRead(IdleStateHandler.java:286)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:357)
	  at io.netty.channel.DefaultChannelPipeline$HeadContext.channelRead(DefaultChannelPipeline.java:1410)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:379)
	  at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:365)
	  at io.netty.channel.DefaultChannelPipeline.fireChannelRead(DefaultChannelPipeline.java:919)
	  at io.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe.read(AbstractNioByteChannel.java:166)
	  at io.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:722)
	  at io.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:658)
	  at io.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:584)
	  at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:496)
	  at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:986)
	  at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
	  at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	  at java.lang.Thread.run(Thread.java:833)
```
