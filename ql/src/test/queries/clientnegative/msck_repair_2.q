DROP TABLE IF EXISTS repairtable;

CREATE TABLE repairtable(col STRING) PARTITIONED BY (p1 STRING, p2 STRING);

MSCK TABLE repairtable;

dfs ${system:test.dfs.mkdir} ${system:test.warehouse.dir}/repairtable/p1=c;
dfs -touchz ${system:test.warehouse.dir}/repairtable/p1=c/datafile;

MSCK TABLE default.repairtable;

MSCK REPAIR TABLE default.repairtable;

MSCK TABLE repairtable;

DROP TABLE default.repairtable;
