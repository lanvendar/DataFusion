package com.datafusion.scheduler.master.actor.core;

import com.datafusion.scheduler.master.actor.Actor;
import com.datafusion.scheduler.master.actor.ActorProxy;
import com.datafusion.scheduler.master.actor.ActorSysContext;
import lombok.Getter;

@Getter
public abstract class AbstractActor implements Actor {
    /**
     * Actor 系统上下文.
     */
    protected ActorSysContext actorSysContext;

    @Override
    public void init(ActorSysContext actorSysContext) {
        this.actorSysContext = actorSysContext;
    }

    @Override
    public ActorProxy getActorProxy() {
        return actorSysContext;
    }
}
