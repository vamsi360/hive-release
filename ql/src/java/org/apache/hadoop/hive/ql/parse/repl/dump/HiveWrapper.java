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
package org.apache.hadoop.hive.ql.parse.repl.dump;

import org.apache.hadoop.hive.metastore.api.Database;
import org.apache.hadoop.hive.ql.metadata.Hive;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.metadata.Table;
import org.apache.hadoop.hive.ql.parse.ReplicationSpec;

/**
 * The idea for this class is that since we need to make sure that
 * we query the replication id from the db before we do any queries
 * to get the object from metastore like tables/functions/partitions etc
 * we are devising this wrapper to wrap all such ordering of statements here.
 */

public class HiveWrapper {
  private final Hive db;
  private final String dbName;
  private final Tuple.Function<ReplicationSpec> functionForSpec;

  public HiveWrapper(Hive db, String dbName) {
    this.dbName = dbName;
    this.db = db;
    this.functionForSpec = new BootStrapReplicationSpecFunction(db);
  }

  public Tuple<org.apache.hadoop.hive.metastore.api.Function> function(final String name)
      throws HiveException {
    return new Tuple<>(functionForSpec, new FunctionObjectFunction(db, dbName, name));
  }

  public Tuple<Database> database() throws HiveException {
    return new Tuple<>(functionForSpec, new DatabaseObjectFunction(db, dbName));
  }

  public Tuple<Table> table(final String tableName) throws HiveException {
    return new Tuple<>(functionForSpec, new TableObjectFunction(db, dbName, tableName));
  }

  private static class FunctionObjectFunction implements Tuple.Function<org.apache.hadoop.hive.metastore.api.Function> {
    private final Hive db;
    private final String dbName;
    private final String name;

    FunctionObjectFunction(Hive db, String dbName, String name) {
      this.db = db;
      this.dbName = dbName;
      this.name = name;
    }

    @Override
    public org.apache.hadoop.hive.metastore.api.Function fromMetaStore() throws HiveException {
      return db.getFunction(dbName, name);
    }
  }

  private static class DatabaseObjectFunction implements Tuple.Function<Database> {
    private final Hive db;
    private final String dbName;

    DatabaseObjectFunction(Hive db, String dbName) {
      this.db = db;
      this.dbName = dbName;
    }

    @Override
    public Database fromMetaStore() throws HiveException {
      return db.getDatabase(dbName);
    }
  }

  private static class TableObjectFunction implements Tuple.Function<Table> {
    private final Hive db;
    private final String tableName, dbName;

    private TableObjectFunction(Hive db, String dbName, String tableName) {
      this.db = db;
      this.tableName = tableName;
      this.dbName = dbName;
    }

    @Override
    public Table fromMetaStore() throws HiveException {
      return db.getTable(dbName, tableName);
    }
  }

  public static class Tuple<T> {

    interface Function<T> {
      T fromMetaStore() throws HiveException;
    }

    public final ReplicationSpec replicationSpec;
    public final T object;

    /**
     * we have to get the replicationspec before we query for the function object
     * from the hive metastore as the spec creation captures the latest event id for replication
     * and we dont want to miss any events hence we are ok replaying some events as part of
     * incremental load to achieve a consistent state of the warehouse.
     */
    Tuple(Function<ReplicationSpec> replicationSpecFunction,
        Function<T> functionForObject) throws HiveException {
      this.replicationSpec = replicationSpecFunction.fromMetaStore();
      this.object = functionForObject.fromMetaStore();
    }
  }
}
