/**
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

package org.apache.hadoop.hive.ql.exec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.apache.hadoop.hive.ql.exec.Utilities.getFileExtension;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.exec.mr.ExecDriver;
import org.apache.hadoop.hive.ql.exec.spark.SparkTask;
import org.apache.hadoop.hive.ql.exec.tez.TezTask;
import org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.metadata.Table;
import org.apache.hadoop.hive.ql.plan.DependencyCollectionWork;
import org.apache.hadoop.hive.ql.plan.DynamicPartitionCtx;
import org.apache.hadoop.hive.ql.plan.ExprNodeConstantDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeGenericFuncDesc;
import org.apache.hadoop.hive.ql.plan.FileSinkDesc;
import org.apache.hadoop.hive.ql.session.SessionState;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFFromUtcTimestamp;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.apache.hadoop.mapred.JobConf;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.google.common.io.Files;

public class TestUtilities {

  public static final Log LOG = LogFactory.getLog(TestUtilities.class);
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final int NUM_BUCKETS = 3;

  @Test
  public void testGetFileExtension() {
    JobConf jc = new JobConf();
    assertEquals("No extension for uncompressed unknown format", "",
        getFileExtension(jc, false, null));
    assertEquals("No extension for compressed unknown format", "",
        getFileExtension(jc, true, null));
    assertEquals("No extension for uncompressed text format", "",
        getFileExtension(jc, false, new HiveIgnoreKeyTextOutputFormat()));
    assertEquals("Deflate for uncompressed text format", ".deflate",
        getFileExtension(jc, true, new HiveIgnoreKeyTextOutputFormat()));
    assertEquals("No extension for uncompressed default format", "",
        getFileExtension(jc, false));
    assertEquals("Deflate for uncompressed default format", ".deflate",
        getFileExtension(jc, true));

    String extension = ".myext";
    jc.set("hive.output.file.extension", extension);
    assertEquals("Custom extension for uncompressed unknown format", extension,
        getFileExtension(jc, false, null));
    assertEquals("Custom extension for compressed unknown format", extension,
        getFileExtension(jc, true, null));
    assertEquals("Custom extension for uncompressed text format", extension,
        getFileExtension(jc, false, new HiveIgnoreKeyTextOutputFormat()));
    assertEquals("Custom extension for uncompressed text format", extension,
        getFileExtension(jc, true, new HiveIgnoreKeyTextOutputFormat()));
  }

  @Test
  public void testSerializeTimestamp() {
    Timestamp ts = new Timestamp(1374554702000L);
    ts.setNanos(123456);
    ExprNodeConstantDesc constant = new ExprNodeConstantDesc(ts);
    List<ExprNodeDesc> children = new ArrayList<ExprNodeDesc>(1);
    children.add(constant);
    ExprNodeGenericFuncDesc desc = new ExprNodeGenericFuncDesc(TypeInfoFactory.timestampTypeInfo,
        new GenericUDFFromUtcTimestamp(), children);
    assertEquals(desc.getExprString(), Utilities.deserializeExpression(
        Utilities.serializeExpression(desc)).getExprString());
  }

  @Test
  public void testgetDbTableName() throws HiveException{
    String tablename;
    String [] dbtab;
    SessionState.start(new HiveConf(this.getClass()));
    String curDefaultdb = SessionState.get().getCurrentDatabase();

    //test table without db portion
    tablename = "tab1";
    dbtab = Utilities.getDbTableName(tablename);
    assertEquals("db name", curDefaultdb, dbtab[0]);
    assertEquals("table name", tablename, dbtab[1]);

    //test table with db portion
    tablename = "dab1.tab1";
    dbtab = Utilities.getDbTableName(tablename);
    assertEquals("db name", "dab1", dbtab[0]);
    assertEquals("table name", "tab1", dbtab[1]);

    //test invalid table name
    tablename = "dab1.tab1.x1";
    try {
      dbtab = Utilities.getDbTableName(tablename);
      fail("exception was expected for invalid table name");
    } catch(HiveException ex){
      assertEquals("Invalid table name " + tablename, ex.getMessage());
    }
  }

  @Test
  public void testGetJarFilesByPath() {
    HiveConf conf = new HiveConf(this.getClass());
    File f = Files.createTempDir();
    String jarFileName1 = f.getAbsolutePath() + File.separator + "a.jar";
    String jarFileName2 = f.getAbsolutePath() + File.separator + "b.jar";
    File jarFile = new File(jarFileName1);
    try {
      FileUtils.touch(jarFile);
      Set<String> jars = Utilities.getJarFilesByPath(f.getAbsolutePath(), conf);
      Assert.assertEquals(Sets.newHashSet("file://" + jarFileName1),jars);

      jars = Utilities.getJarFilesByPath("/folder/not/exist", conf);
      Assert.assertTrue(jars.isEmpty());

      File jarFile2 = new File(jarFileName2);
      FileUtils.touch(jarFile2);
      String newPath = "file://" + jarFileName1 + "," + "file://" + jarFileName2 + ",/file/not/exist";
      jars = Utilities.getJarFilesByPath(newPath, conf);
      Assert.assertEquals(Sets.newHashSet("file://" + jarFileName1, "file://" + jarFileName2), jars);
    } catch (IOException e) {
      LOG.error("failed to copy file to reloading folder", e);
      Assert.fail(e.getMessage());
    } finally {
      FileUtils.deleteQuietly(f);
    }
  }

  @Test
  public void testRemoveTempOrDuplicateFilesOnTezNoDp() throws Exception {
    List<Path> paths = runRemoveTempOrDuplicateFilesTestCase("tez", false);
    assertEquals(0, paths.size());
  }

  @Test
  public void testRemoveTempOrDuplicateFilesOnTezWithDp() throws Exception {
    List<Path> paths = runRemoveTempOrDuplicateFilesTestCase("tez", true);
    assertEquals(0, paths.size());
  }

  @Test
  public void testRemoveTempOrDuplicateFilesOnMrNoDp() throws Exception {
    List<Path> paths = runRemoveTempOrDuplicateFilesTestCase("mr", false);
    assertEquals(NUM_BUCKETS, paths.size());
  }

  @Test
  public void testRemoveTempOrDuplicateFilesOnMrWithDp() throws Exception {
    List<Path> paths = runRemoveTempOrDuplicateFilesTestCase("mr", true);
    assertEquals(NUM_BUCKETS, paths.size());
  }

  private List<Path> runRemoveTempOrDuplicateFilesTestCase(String executionEngine, boolean dPEnabled)
      throws Exception {
    Configuration hconf = new HiveConf(this.getClass());
    // do this to verify that Utilities.removeTempOrDuplicateFiles does not revert to default scheme information
    hconf.set("fs.defaultFS", "hdfs://should-not-be-used/");
    hconf.set(HiveConf.ConfVars.HIVE_EXECUTION_ENGINE.varname, executionEngine);
    FileSystem localFs = FileSystem.getLocal(hconf);
    DynamicPartitionCtx dpCtx = getDynamicPartitionCtx(dPEnabled);
    Path tempDirPath = setupTempDirWithSingleOutputFile(hconf);
    FileSinkDesc conf = getFileSinkDesc(tempDirPath);

    List<Path> paths = Utilities.removeTempOrDuplicateFiles(localFs, tempDirPath, dpCtx, conf, hconf);

    String expectedScheme = tempDirPath.toUri().getScheme();
    String expectedAuthority = tempDirPath.toUri().getAuthority();
    assertPathsMatchSchemeAndAuthority(expectedScheme, expectedAuthority, paths);

    return paths;
  }

  private void assertPathsMatchSchemeAndAuthority(String expectedScheme, String expectedAuthority, List<Path> paths) {
    for (Path path : paths) {
      assertEquals(path.toUri().getScheme().toLowerCase(), expectedScheme.toLowerCase());
      assertEquals(path.toUri().getAuthority(), expectedAuthority);
    }
  }

  private DynamicPartitionCtx getDynamicPartitionCtx(boolean dPEnabled) {
    DynamicPartitionCtx dpCtx = null;
    if (dPEnabled) {
      dpCtx = mock(DynamicPartitionCtx.class);
      when(dpCtx.getNumDPCols()).thenReturn(0);
      when(dpCtx.getNumBuckets()).thenReturn(NUM_BUCKETS);
    }
    return dpCtx;
  }

  private FileSinkDesc getFileSinkDesc(Path tempDirPath) {
    Table table = mock(Table.class);
    when(table.getNumBuckets()).thenReturn(NUM_BUCKETS);
    FileSinkDesc conf = new FileSinkDesc(tempDirPath, null, false);
    conf.setTable(table);
    return conf;
  }

  private Path setupTempDirWithSingleOutputFile(Configuration hconf) throws IOException {
    Path tempDirPath = new Path("file://" + temporaryFolder.newFolder().getAbsolutePath());
    Path taskOutputPath = new Path(tempDirPath, Utilities.getTaskId(hconf));
    FileSystem.getLocal(hconf).create(taskOutputPath).close();
    return tempDirPath;
  }

  private Task<? extends Serializable> getDependencyCollectionTask(){
    return TaskFactory.get(new DependencyCollectionWork(), new HiveConf());
  }

  /**
   * Generates a task graph that looks like this:
   *
   *       ---->DTa----
   *      /            \
   * root ----->DTb-----*-->DTd---> ProvidedTask --> DTe
   *      \            /
   *       ---->DTc----
   */
  private List<Task<? extends Serializable>> getTestDiamondTaskGraph(Task<? extends Serializable> providedTask){
    // Note: never instantiate a task without TaskFactory.get() if you're not
    // okay with .equals() breaking. Doing it via TaskFactory.get makes sure
    // that an id is generated, and two tasks of the same type don't show
    // up as "equal", which is important for things like iterating over an
    // array. Without this, DTa, DTb, and DTc would show up as one item in
    // the list of children. Thus, we're instantiating via a helper method
    // that instantiates via TaskFactory.get()
    Task<? extends Serializable> root = getDependencyCollectionTask();
    Task<? extends Serializable> DTa = getDependencyCollectionTask();
    Task<? extends Serializable> DTb = getDependencyCollectionTask();
    Task<? extends Serializable> DTc = getDependencyCollectionTask();
    Task<? extends Serializable> DTd = getDependencyCollectionTask();
    Task<? extends Serializable> DTe = getDependencyCollectionTask();

    root.addDependentTask(DTa);
    root.addDependentTask(DTb);
    root.addDependentTask(DTc);

    DTa.addDependentTask(DTd);
    DTb.addDependentTask(DTd);
    DTc.addDependentTask(DTd);

    DTd.addDependentTask(providedTask);

    providedTask.addDependentTask(DTe);

    List<Task<? extends Serializable>> retVals = new ArrayList<Task<? extends Serializable>>();
    retVals.add(root);
    return retVals;
  }

  /**
   * DependencyCollectionTask that counts how often getDependentTasks on it
   * (and thus, on its descendants) is called counted via Task.getDependentTasks.
   * It is used to wrap another task to intercept calls on it.
   */
  public class CountingWrappingTask extends DependencyCollectionTask {
    int count;
    Task<? extends Serializable> wrappedDep = null;

    public CountingWrappingTask(Task<? extends Serializable> dep) {
      count = 0;
      wrappedDep = dep;
      super.addDependentTask(wrappedDep);
    }

    public boolean addDependentTask(Task<? extends Serializable> dependent) {
      return wrappedDep.addDependentTask(dependent);
    }

    @Override
    public List<Task<? extends Serializable>> getDependentTasks() {
      count++;
      System.err.println("YAH:getDepTasks got called!");
      (new Exception()).printStackTrace(System.err);
      LOG.info("YAH!getDepTasks", new Exception());
      return super.getDependentTasks();
    }

    public int getDepCallCount() {
      return count;
    }

    @Override
    public String getName() {
      return "COUNTER_TASK";
    }

    @Override
    public String toString() {
      return getName() + "_" + wrappedDep.toString();
    }
  };

  /**
   * This test tests that Utilities.get*Tasks do not repeat themselves in the process
   * of extracting tasks from a given set of root tasks when given DAGs that can have
   * multiple paths, such as the case with Diamond-shaped DAGs common to replication.
   */
  @Test
  public void testGetTasksHaveNoRepeats() {

    CountingWrappingTask mrTask = new CountingWrappingTask(new ExecDriver());
    CountingWrappingTask tezTask = new CountingWrappingTask(new TezTask());
    CountingWrappingTask sparkTask = new CountingWrappingTask(new SparkTask());

    // First check - we should not have repeats in results
    assertEquals("No repeated MRTasks from Utilities.getMRTasks", 1,
        Utilities.getMRTasks(getTestDiamondTaskGraph(mrTask)).size());
    assertEquals("No repeated TezTasks from Utilities.getTezTasks", 1,
        Utilities.getTezTasks(getTestDiamondTaskGraph(tezTask)).size());
    assertEquals("No repeated TezTasks from Utilities.getSparkTasks", 1,
        Utilities.getSparkTasks(getTestDiamondTaskGraph(sparkTask)).size());

    // Second check - the tasks we looked for must not have been accessed more than
    // once as a result of the traversal (note that we actually wind up accessing
    // 2 times , because each visit counts twice, once to check for existence, and
    // once to visit.

    assertEquals("MRTasks should have been visited only once", 2, mrTask.getDepCallCount());
    assertEquals("TezTasks should have been visited only once", 2, tezTask.getDepCallCount());
    assertEquals("SparkTasks should have been visited only once", 2, sparkTask.getDepCallCount());

  }

}
