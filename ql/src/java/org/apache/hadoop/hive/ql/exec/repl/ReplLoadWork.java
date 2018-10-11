/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.ql.exec.repl;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.exec.Task;
import org.apache.hadoop.hive.ql.exec.repl.bootstrap.events.DatabaseEvent;
import org.apache.hadoop.hive.ql.exec.repl.bootstrap.events.filesystem.BootstrapEventsIterator;
import org.apache.hadoop.hive.ql.exec.repl.incremental.IncrementalLoadTasksBuilder;
import org.apache.hadoop.hive.ql.plan.Explain;
import org.apache.hadoop.hive.ql.session.LineageState;
import org.apache.hadoop.hive.ql.exec.repl.incremental.IncrementalLoadEventsIterator;

import java.io.IOException;
import java.io.Serializable;

@Explain(displayName = "Replication Load Operator", explainLevels = { Explain.Level.USER,
    Explain.Level.DEFAULT,
    Explain.Level.EXTENDED })
public class ReplLoadWork implements Serializable {
  final String dbNameToLoadIn;
  final String tableNameToLoadIn;
  final String dumpDirectory;
  private final BootstrapEventsIterator bootstrapIterator;
  private final IncrementalLoadEventsIterator incrementalIterator;
  private int loadTaskRunCount = 0;
  private DatabaseEvent.State state = null;
  private final IncrementalLoadTasksBuilder incrementalLoadTaskBuilder;
  private Task<? extends Serializable> rootTask;

  /**
   * These are sessionState objects that are copied over to work to allow for parallel execution.
   * Based on the current use case the methods are selectively synchronized, which might need to be
   * taken care when using other methods.
   */
  final LineageState sessionStateLineageState;

  public ReplLoadWork(HiveConf hiveConf, String dumpDirectory, String dbNameToLoadIn,
      String tableNameToLoadIn, LineageState lineageState, boolean isIncrementalDump) throws IOException {
    this.tableNameToLoadIn = tableNameToLoadIn;
    this.dumpDirectory = dumpDirectory;
    if (isIncrementalDump) {
      incrementalIterator = new IncrementalLoadEventsIterator(dumpDirectory, hiveConf);
      this.bootstrapIterator = null;
      incrementalLoadTaskBuilder = new IncrementalLoadTasksBuilder(dbNameToLoadIn, tableNameToLoadIn, dumpDirectory,
              incrementalIterator, hiveConf);
    } else {
      this.bootstrapIterator = new BootstrapEventsIterator(dumpDirectory, dbNameToLoadIn, hiveConf);
      incrementalIterator = null;
      incrementalLoadTaskBuilder = null;
    }this.dbNameToLoadIn = dbNameToLoadIn;
    sessionStateLineageState = lineageState;
    rootTask = null;
  }

  public BootstrapEventsIterator iterator() {
    return bootstrapIterator;
  }

  int executedLoadTask() {
    return ++loadTaskRunCount;
  }

  void updateDbEventState(DatabaseEvent.State state) {
    this.state = state;
  }

  DatabaseEvent databaseEvent(HiveConf hiveConf) {
    return state.toEvent(hiveConf);
  }

  boolean hasDbState() {
    return state != null;
  }

  public boolean isIncrementalLoad() {
    return incrementalIterator != null;
  }

  public IncrementalLoadEventsIterator getIncrementalIterator() {
    return incrementalIterator;
  }

  public IncrementalLoadTasksBuilder getIncrementalLoadTaskBuilder() {
    return incrementalLoadTaskBuilder;
  }

  public Task<? extends Serializable> getRootTask() {
    return rootTask;
  }

  public void setRootTask(Task<? extends Serializable> rootTask) {
    this.rootTask = rootTask;
  }
}