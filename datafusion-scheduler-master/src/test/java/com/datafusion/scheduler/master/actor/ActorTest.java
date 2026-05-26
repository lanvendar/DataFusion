package com.datafusion.scheduler.master.actor;

import com.datafusion.common.options.Options;
import com.datafusion.scheduler.master.ActorType;
import com.datafusion.scheduler.master.actor.core.AbstractActor;
import com.datafusion.scheduler.master.actor.core.DefaultActorSystem;
import com.datafusion.scheduler.master.actor.enums.ActorTypeEnum;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Actor 模块使用示例.
 *
 * @author lanvendar
 * @version 1.0.0, 2026/2/27
 * @since 2026/2/27
 */
public class ActorTest {

    private static final Logger log = LoggerFactory.getLogger(ActorTest.class);

    /**
     * 测试 ActorSystem 基本功能.
     */
    @Test
    public void testActorSystem() {
        // 创建 ActorSystem
        ActorSystem actorSystem = new DefaultActorSystem(Executors.newFixedThreadPool(4), new Options());

        // 创建 Actor
        ActorProxy actor = actorSystem.createParentActor(new Actor.Creator() {
            @Override
            public String createActorId() {
                return "test-actor-001";
            }

            @Override
            public Actor createActor() {
                return new AbstractActor() {
                    @Override
                    public String getActorId() {
                        return "test-actor-001";
                    }

                    @Override
                    public ActorTypeEnum type() {
                        return ActorType.FLOW;
                    }

                    @Override
                    public void process(ActorMsg msg) {
                        log.info("处理消息: {}", msg.getMsgType());
                    }
                };
            }
        });

        // 获取 Actor
        ActorProxy proxy = actorSystem.getActor("test-actor-001");
        assertNotNull(proxy);

        log.info("Actor 测试通过");
    }

    /**
     * 测试 Actor 消息通信.
     */
    @Test
    public void testActorMessage() throws InterruptedException {
        // 创建 ActorSystem
        ActorSystem actorSystem = new DefaultActorSystem(Executors.newFixedThreadPool(4), new Options());

        AtomicInteger count = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        // 创建 Actor
        actorSystem.createParentActor(new Actor.Creator() {
            @Override
            public String createActorId() {
                return "message-actor-001";
            }

            @Override
            public Actor createActor() {
                return new AbstractActor() {
                    @Override
                    public String getActorId() {
                        return "message-actor-001";
                    }

                    @Override
                    public ActorTypeEnum type() {
                        return ActorType.TASK;
                    }

                    @Override
                    public void process(ActorMsg msg) {
                        log.info("收到消息: {}", msg.getMsgType());
                        count.incrementAndGet();
                        latch.countDown();
                    }
                };
            }
        });

        // 发送消息
        actorSystem.notify("message-actor-001", () -> "TEST_MSG");

        // 等待消息处理
        boolean await = latch.await(5, TimeUnit.SECONDS);
        assertTrue(await);
        assertEquals(1, count.get());

        log.info("消息通信测试通过");
    }

    /**
     * 测试 Actor 父子层级关系.
     */
    @Test
    public void testParentChildActor() {
        // 创建 ActorSystem
        ActorSystem actorSystem = new DefaultActorSystem(Executors.newFixedThreadPool(4), new Options());

        // 创建父 Actor
        ActorProxy parentActor = actorSystem.createParentActor(new Actor.Creator() {
            @Override
            public String createActorId() {
                return "parent-actor-001";
            }

            @Override
            public Actor createActor() {
                return new AbstractActor() {
                    @Override
                    public String getActorId() {
                        return "parent-actor-001";
                    }

                    @Override
                    public ActorTypeEnum type() {
                        return ActorType.FLOW;
                    }

                    @Override
                    public void process(ActorMsg msg) {
                        log.info("父 Actor 处理消息: {}", msg.getMsgType());
                    }
                };
            }
        });

        // 创建子 Actor
        ActorProxy childActor = actorSystem.createChildActor(new Actor.Creator() {
            @Override
            public String createActorId() {
                return "child-actor-001";
            }

            @Override
            public Actor createActor() {
                return new AbstractActor() {
                    @Override
                    public String getActorId() {
                        return "child-actor-001";
                    }

                    @Override
                    public ActorTypeEnum type() {
                        return ActorType.TASK;
                    }

                    @Override
                    public void process(ActorMsg msg) {
                        log.info("子 Actor 处理消息: {}", msg.getMsgType());
                    }
                };
            }
        }, "parent-actor-001");

        // 验证父子关系
        assertNotNull(parentActor);
        assertNotNull(childActor);

        // 广播消息给子 Actor
        actorSystem.broadcastToChildren("parent-actor-001", () -> "BROADCAST_MSG");

        log.info("父子层级 Actor 测试通过");
    }

    /**
     * 测试 Actor 停止功能.
     */
    @Test
    public void testActorStop() throws InterruptedException {
        // 创建 ActorSystem
        ActorSystem actorSystem = new DefaultActorSystem(Executors.newFixedThreadPool(4), new Options());

        // 创建 Actor
        actorSystem.createParentActor(new Actor.Creator() {
            @Override
            public String createActorId() {
                return "stop-actor-001";
            }

            @Override
            public Actor createActor() {
                return new AbstractActor() {
                    @Override
                    public String getActorId() {
                        return "stop-actor-001";
                    }

                    @Override
                    public ActorTypeEnum type() {
                        return ActorType.FLOW;
                    }

                    @Override
                    public void process(ActorMsg msg) {
                        log.info("处理消息: {}", msg.getMsgType());
                    }
                };
            }
        });

        // 停止 Actor
        actorSystem.stop();

        // 验证 Actor 已停止
        ActorProxy proxy = actorSystem.getActor("stop-actor-001");
        assertNull(proxy);

        log.info("Actor 停止测试通过");
    }
}
