/**
 * Copyright (c) 2011 Yahoo! Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. See accompanying LICENSE file.
 */

package com.yahoo.omid;

import static org.junit.Assert.assertTrue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.omid.client.TransactionManager;
import com.yahoo.omid.client.TransactionState;
import com.yahoo.omid.client.TransactionalTable;

public class TestBasicTransaction extends OmidTestBase {
   private static final Log LOG = LogFactory.getLog(TestBasicTransaction.class);

   private static final String TEST_TABLE = "test";
   private static final String TEST_FAMILY = "data";

   @Before public void setUp() throws Exception {
      HBaseAdmin admin = new HBaseAdmin(conf);
      
      if (!admin.tableExists(TEST_TABLE)) {
         HTableDescriptor desc = new HTableDescriptor(TEST_TABLE);
         HColumnDescriptor datafam = new HColumnDescriptor(TEST_FAMILY);
         datafam.setMaxVersions(10);
         desc.addFamily(datafam);
         
         admin.createTable(desc);
      }

      admin.enableTable(TEST_TABLE);
      HTableDescriptor[] tables = admin.listTables();
      for (HTableDescriptor t : tables) {
         LOG.info(t.getNameAsString());
      }
   }

   @After public void tearDown() {
      try {
         LOG.info("tearing Down");
         HBaseAdmin admin = new HBaseAdmin(conf);
         admin.disableTable(TEST_TABLE);
         admin.deleteTable(TEST_TABLE);
         
      } catch (Exception e) {
         LOG.error("Error tearing down", e);
      }
   }
   

   @Test public void runTestSimple() throws Exception {
      try {
         TransactionManager tm = new TransactionManager(conf);
         TransactionalTable tt = new TransactionalTable(conf, TEST_TABLE);
         
         TransactionState t1 = tm.beginTransaction();
         LOG.info("Transaction created " + t1);
         
         byte[] row = Bytes.toBytes("test-simple");
         byte[] fam = Bytes.toBytes(TEST_FAMILY);
         byte[] col = Bytes.toBytes("testdata");
         byte[] data1 = Bytes.toBytes("testWrite-1");
         byte[] data2 = Bytes.toBytes("testWrite-2");

         Put p = new Put(row);
         p.add(fam, col, data1);
         tt.put(t1, p);
         tm.tryCommit(t1);

         TransactionState tread = tm.beginTransaction();
         TransactionState t2 = tm.beginTransaction();
         p = new Put(row);
         p.add(fam, col, data2);
         tt.put(t2, p);
         tm.tryCommit(t2);

         Get g = new Get(row).setMaxVersions(1);
         Result r = tt.get(g);
         assertTrue("Unexpected value for read: " + Bytes.toString(r.getValue(fam, col)),
                    Bytes.equals(data2, r.getValue(fam, col)));

         r = tt.get(tread, g);
         assertTrue("Unexpected value for SI read " + tread + ": " + Bytes.toString(r.getValue(fam, col)),
                    Bytes.equals(data1, r.getValue(fam, col)));
      } catch (Exception e) {
         LOG.error("Exception occurred", e);
         throw e;
      }
   }

   @Test public void runTestManyVersions() throws Exception {
      try {
         TransactionManager tm = new TransactionManager(conf);
         TransactionalTable tt = new TransactionalTable(conf, TEST_TABLE);

         TransactionState t1 = tm.beginTransaction();
         LOG.info("Transaction created " + t1);

         byte[] row = Bytes.toBytes("test-simple");
         byte[] fam = Bytes.toBytes(TEST_FAMILY);
         byte[] col = Bytes.toBytes("testdata");
         byte[] data1 = Bytes.toBytes("testWrite-1");
         byte[] data2 = Bytes.toBytes("testWrite-2");

         Put p = new Put(row);
         p.add(fam, col, data1);
         tt.put(t1, p);
         tm.tryCommit(t1);

         for (int i = 0; i < 5; ++i) {
            TransactionState t2 = tm.beginTransaction();
            p = new Put(row);
            p.add(fam, col, data2);
            tt.put(t2, p);
         }
         TransactionState tread = tm.beginTransaction();

         Get g = new Get(row).setMaxVersions(1);
         Result r = tt.get(g);
         assertTrue("Unexpected value for read: " + Bytes.toString(r.getValue(fam, col)),
               Bytes.equals(data2, r.getValue(fam, col)));

         r = tt.get(tread, g);
         assertTrue("Unexpected value for SI read " + tread + ": " + Bytes.toString(r.getValue(fam, col)),
               Bytes.equals(data1, r.getValue(fam, col)));
      } catch (Exception e) {
         LOG.error("Exception occurred", e);
         throw e;
      }
   }

   @Test public void runTestInterleave() throws Exception {
      try {
         TransactionManager tm = new TransactionManager(conf);
         TransactionalTable tt = new TransactionalTable(conf, TEST_TABLE);
        
         TransactionState t1 = tm.beginTransaction();
         LOG.info("Transaction created " + t1);
         
         byte[] row = Bytes.toBytes("test-interleave");
         byte[] fam = Bytes.toBytes(TEST_FAMILY);
         byte[] col = Bytes.toBytes("testdata");
         byte[] data1 = Bytes.toBytes("testWrite-1");
         byte[] data2 = Bytes.toBytes("testWrite-2");

         Put p = new Put(row);
         p.add(fam, col, data1);
         tt.put(t1, p);
         tm.tryCommit(t1);

         TransactionState t2 = tm.beginTransaction();
         p = new Put(row);
         p.add(fam, col, data2);
         tt.put(t2, p);

         TransactionState tread = tm.beginTransaction();
         Get g = new Get(row).setMaxVersions(1);
         Result r = tt.get(tread, g);
         assertTrue("Unexpected value for SI read " + tread + ": " + Bytes.toString(r.getValue(fam, col)),
                    Bytes.equals(data1, r.getValue(fam, col)));
         tm.tryCommit(t2);

         r = tt.get(g);
         assertTrue("Unexpected value for read: " + Bytes.toString(r.getValue(fam, col)),
                    Bytes.equals(data2, r.getValue(fam, col)));

      } catch (Exception e) {
         LOG.error("Exception occurred", e);
         throw e;
      }
   }

   @Test public void runTestInterleaveScan() throws Exception {
      try {
         TransactionManager tm = new TransactionManager(conf);
         TransactionalTable tt = new TransactionalTable(conf, TEST_TABLE);
         
         TransactionState t1 = tm.beginTransaction();
         LOG.info("Transaction created " + t1);
         
         byte[] fam = Bytes.toBytes(TEST_FAMILY);
         byte[] col = Bytes.toBytes("testdata");
         byte[] data1 = Bytes.toBytes("testWrite-1");
         byte[] data2 = Bytes.toBytes("testWrite-2");
         
         byte[] startrow = Bytes.toBytes("test-scan" + 0);
         byte[] stoprow = Bytes.toBytes("test-scan" + 9);
         byte[] modrow = Bytes.toBytes("test-scan" + 3);
         for (int i = 0; i < 10; i++) {
            byte[] row = Bytes.toBytes("test-scan" + i);
            
            Put p = new Put(row);
            p.add(fam, col, data1);
            tt.put(t1, p);
         }
         tm.tryCommit(t1);

         TransactionState t2 = tm.beginTransaction();
         Put p = new Put(modrow);
         p.add(fam, col, data2);
         tt.put(t2, p);
         
         TransactionState tscan = tm.beginTransaction();
         ResultScanner rs = tt.getScanner(tscan, new Scan().setStartRow(startrow).setStopRow(stoprow));
         Result r = rs.next();
         int i = 0;
         while (r != null) {
            if (LOG.isTraceEnabled()) {
               LOG.trace("Scan1 :" + Bytes.toString(r.getRow()) + " => " + Bytes.toString(r.getValue(fam, col)));
            }
            System.out.println(++i);

            assertTrue("Unexpected value for SI scan " + tscan + ": " + Bytes.toString(r.getValue(fam, col)),
                       Bytes.equals(data1, r.getValue(fam, col)));
            r = rs.next();
         }
         tm.tryCommit(t2);

         int modifiedrows = 0;
         tscan = tm.beginTransaction();
         rs = tt.getScanner(tscan, new Scan().setStartRow(startrow).setStopRow(stoprow));
         r = rs.next();
         while (r != null) {
            if (Bytes.equals(data2, r.getValue(fam, col))) {
               if (LOG.isTraceEnabled()) {
                  LOG.trace("Modified :" + Bytes.toString(r.getRow()));
               }
               modifiedrows++;
            }
            
            r = rs.next();
         }
         
         assertTrue("Expected 1 row modified, but " + modifiedrows + " are.", 
                    modifiedrows == 1);

      } catch (Exception e) {
         LOG.error("Exception occurred", e);
         throw e;
      }
   }
   
   @Test public void runTestDeleteCol() throws Exception {
      try {
         TransactionManager tm = new TransactionManager(conf);
         TransactionalTable tt = new TransactionalTable(conf, TEST_TABLE);
         
         TransactionState t1 = tm.beginTransaction();
         LOG.info("Transaction created " + t1);
         
         int rowcount = 10;
         int colAcount = 0;
         int colBcount = 0;

         byte[] fam = Bytes.toBytes(TEST_FAMILY);
         byte[] colA = Bytes.toBytes("testdataA");
         byte[] colB = Bytes.toBytes("testdataB");
         byte[] data1 = Bytes.toBytes("testWrite-1");
         byte[] data2 = Bytes.toBytes("testWrite-2");
         
         byte[] modrow = Bytes.toBytes("test-del" + 3);
         for (int i = 0; i < rowcount; i++) {
            byte[] row = Bytes.toBytes("test-del" + i);
            
            Put p = new Put(row);
            p.add(fam, colA, data1);
            p.add(fam, colB, data2);
            tt.put(t1, p);
         }
         tm.tryCommit(t1);

         TransactionState t2 = tm.beginTransaction();
         Delete d = new Delete(modrow);
         d.deleteColumn(fam, colA);
         tt.delete(t2, d);
         
         TransactionState tscan = tm.beginTransaction();
         ResultScanner rs = tt.getScanner(tscan, new Scan());
         Result r = rs.next();
         colAcount = 0;
         colBcount = 0;
         while (r != null) {
            if (r.containsColumn(fam, colA)) {
               colAcount++;
            }
            if (r.containsColumn(fam, colB)) {
               colBcount++;
            }

            LOG.trace("row: " + Bytes.toString(r.getRow()) + " countA: " + colAcount + " countB: " + colBcount);
            r = rs.next();
         }
         assertTrue("Expected all these numbers to be the same " 
                    + colAcount + "," + colBcount + "," + rowcount,
                    (colAcount == colBcount) && (colAcount == rowcount));
         tm.tryCommit(t2);

         tscan = tm.beginTransaction();
         rs = tt.getScanner(tscan, new Scan());
         r = rs.next();

         colAcount = 0;
         colBcount = 0;
         while (r != null) {
            if (r.containsColumn(fam, colA)) {
               colAcount++;
            }
            if (r.containsColumn(fam, colB)) {
               colBcount++;
            }

            LOG.trace("row: " + Bytes.toString(r.getRow()) + " countA: " + colAcount + " countB: " + colBcount);
            r = rs.next();
         }
         assertTrue("Expected colAcount to be " + (rowcount-1) 
                    + " but it is " + colAcount,
                    colAcount ==  (rowcount-1));
         assertTrue("Expected colBcount to be " + rowcount 
                    + " but it is " + colBcount,
                    colBcount ==  rowcount);
      } catch (Exception e) {
         LOG.error("Exception occurred", e);
         throw e;
      }
   }
   
   @Test public void runTestDeleteRow() throws Exception {
      try {
         TransactionManager tm = new TransactionManager(conf);
         TransactionalTable tt = new TransactionalTable(conf, TEST_TABLE);
         
         TransactionState t1 = tm.beginTransaction();
         LOG.info("Transaction created " + t1);
         
         int rowcount = 10;
         int count = 0;

         byte[] fam = Bytes.toBytes(TEST_FAMILY);
         byte[] col = Bytes.toBytes("testdata");
         byte[] data1 = Bytes.toBytes("testWrite-1");
         
         byte[] modrow = Bytes.toBytes("test-del" + 3);
         for (int i = 0; i < rowcount; i++) {
            byte[] row = Bytes.toBytes("test-del" + i);
            
            Put p = new Put(row);
            p.add(fam, col, data1);
            tt.put(t1, p);
         }
         tm.tryCommit(t1);

         TransactionState t2 = tm.beginTransaction();
         Delete d = new Delete(modrow);
         tt.delete(t2, d);
         
         TransactionState tscan = tm.beginTransaction();
         ResultScanner rs = tt.getScanner(tscan, new Scan());
         Result r = rs.next();
         count = 0;
         while (r != null) {
            count++;
            LOG.trace("row: " + Bytes.toString(r.getRow()) + " count: " + count);
            r = rs.next();
         }
         assertTrue("Expected " + rowcount + " rows but " + count + " found",
                    count == rowcount);

         tm.tryCommit(t2);
         
         tscan = tm.beginTransaction();
         rs = tt.getScanner(tscan, new Scan());
         r = rs.next();
         count = 0;
         while (r != null) {
            count++;            
            r = rs.next();
         }
         assertTrue("Expected " + (rowcount-1) + " rows but " + count + " found",
                    count == (rowcount-1));

      } catch (Exception e) {
         LOG.error("Exception occurred", e);
         throw e;
      }
   }
}