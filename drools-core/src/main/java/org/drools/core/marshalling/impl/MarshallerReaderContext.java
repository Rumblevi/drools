/*
 * Copyright 2010 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.core.marshalling.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.HashMap;
import java.util.Map;

import org.drools.core.common.BaseNode;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.impl.InternalKnowledgeBase;
import org.drools.core.marshalling.impl.ProtobufInputMarshaller.PBActivationsFilter;
import org.drools.core.marshalling.impl.ProtobufInputMarshaller.TupleKey;
import org.drools.core.phreak.PhreakTimerNode.Scheduler;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.rule.EntryPointId;
import org.drools.core.spi.PropagationContext;
import org.kie.api.marshalling.ObjectMarshallingStrategy;
import org.kie.api.marshalling.ObjectMarshallingStrategyStore;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieRuntime;
import org.kie.internal.marshalling.MarshallerFactory;

public class MarshallerReaderContext extends ObjectInputStream {
    public final MarshallerReaderContext                                           stream;
    public final InternalKnowledgeBase                                             kBase;
    public InternalWorkingMemory                                                   wm;
    public KieRuntime                                                              kruntime;
    public final Map<Integer, BaseNode>                                            sinks;

    public Map<Long, InternalFactHandle>                                           handles;

    public final Map<RightTupleKey, RightTuple>                                    rightTuples;
    public final Map<Integer, LeftTuple>                                           terminalTupleMap;
    public final PBActivationsFilter                                               filter;

    public final ObjectMarshallingStrategyStore                                    resolverStrategyFactory;
    public final Map<Integer, ObjectMarshallingStrategy>                           usedStrategies;
    public final Map<ObjectMarshallingStrategy, ObjectMarshallingStrategy.Context> strategyContexts;

    public final Map<String, EntryPointId>                                           entryPoints;

    public final Map<Integer, TimersInputMarshaller>                               readersByInt;

    public final Map<Long, PropagationContext>                                     propagationContexts;

    public final boolean                                                           marshalProcessInstances;
    public final boolean                                                           marshalWorkItems;
    public final Environment                                                       env;

    // this is a map to store node memory data indexed by node ID
    private final Map<Integer, Object>                                              nodeMemories;

    public Map<Integer, Object> getNodeMemories() {
        return nodeMemories;
    }

    public Object                                                                  parameterObject;
    public ClassLoader                                                             classLoader;
    public Map<Integer, Map<TupleKey, Scheduler>>                                  timerNodeSchedulers;

    public MarshallerReaderContext(InputStream stream,
                                   InternalKnowledgeBase kBase,
                                   Map<Integer, BaseNode> sinks,
                                   ObjectMarshallingStrategyStore resolverStrategyFactory,
                                   Map<Integer, TimersInputMarshaller> timerReaders,
                                   Environment env) throws IOException {
        this( stream,
              kBase,
              sinks,
              resolverStrategyFactory,
              timerReaders,
              true,
              true,
              env );
    }

    public MarshallerReaderContext(InputStream stream,
                                   InternalKnowledgeBase kBase,
                                   Map<Integer, BaseNode> sinks,
                                   ObjectMarshallingStrategyStore resolverStrategyFactory,
                                   Map<Integer, TimersInputMarshaller> timerReaders,
                                   boolean marshalProcessInstances,
                                   boolean marshalWorkItems,
                                   Environment env) throws IOException {
        super( stream );
        this.stream = this;
        this.kBase = kBase;
        this.sinks = sinks;

        this.readersByInt = timerReaders;

        this.handles = new HashMap<>();
        this.rightTuples = new HashMap<>();
        this.terminalTupleMap = new HashMap<>();
        this.filter = new PBActivationsFilter();
        this.entryPoints = new HashMap<>();
        this.propagationContexts = new HashMap<>();
        if ( resolverStrategyFactory == null ) {
            ObjectMarshallingStrategy[] strats = (ObjectMarshallingStrategy[]) env.get( EnvironmentName.OBJECT_MARSHALLING_STRATEGIES );
            if ( strats == null ) {
                strats = getMarshallingStrategy();
            }
            this.resolverStrategyFactory = new ObjectMarshallingStrategyStoreImpl( strats );
        }
        else {
            this.resolverStrategyFactory = resolverStrategyFactory;
        }
        this.usedStrategies = new HashMap<>();
        this.strategyContexts = new HashMap<>();

        this.marshalProcessInstances = marshalProcessInstances;
        this.marshalWorkItems = marshalWorkItems;
        this.env = env;

        this.nodeMemories = new HashMap<>();
        this.timerNodeSchedulers = new HashMap<>();

        this.parameterObject = null;
    }

    protected ObjectMarshallingStrategy[] getMarshallingStrategy() {
        return new ObjectMarshallingStrategy[]{MarshallerFactory.newSerializeMarshallingStrategy()};
    }

    @Override
    protected Class< ? > resolveClass(ObjectStreamClass desc) throws IOException,
                                                             ClassNotFoundException {
        String name = desc.getName();
        try {
            if ( this.classLoader == null ) {
                if ( this.kBase != null ) {
                    this.classLoader = this.kBase.getRootClassLoader();
                }
            }
            return Class.forName( name, false, this.classLoader );
        } catch ( ClassNotFoundException ex ) {
            return super.resolveClass( desc );
        }
    }
    
    public void addTimerNodeScheduler( int nodeId, TupleKey key, Scheduler scheduler ) {
        Map<TupleKey, Scheduler> timers = timerNodeSchedulers.get( nodeId );
        if( timers == null ) {
            timers = new HashMap<>();
            timerNodeSchedulers.put( nodeId, timers );
        }
        timers.put( key, scheduler );
    }
    
    public Scheduler removeTimerNodeScheduler( int nodeId, TupleKey key ) {
        Map<TupleKey, Scheduler> timers = timerNodeSchedulers.get( nodeId );
        if( timers != null ) {
            Scheduler scheduler = timers.remove( key );
            if( timers.isEmpty() ) {
                timerNodeSchedulers.remove( nodeId );
            }
            return scheduler;
        } 
        return null;
    }

    public void withSerializedNodeMemories() {
        filter.withSerializedNodeMemories();
    }
}
