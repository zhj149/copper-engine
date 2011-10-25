/*
 * Copyright 2002-2011 SCOOP Software GmbH
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
package de.scoopgmbh.copper.test.persistent;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import de.scoopgmbh.copper.EngineState;
import de.scoopgmbh.copper.ProcessingEngine;
import de.scoopgmbh.copper.Workflow;
import de.scoopgmbh.copper.WorkflowFactory;
import de.scoopgmbh.copper.db.utility.RetryingTransaction;
import de.scoopgmbh.copper.persistent.PersistentScottyEngine;
import de.scoopgmbh.copper.test.backchannel.BackChannelQueue;
import de.scoopgmbh.copper.test.backchannel.WorkflowResult;
import de.scoopgmbh.copper.test.persistent.subworkflow.TestParentWorkflow;

public class PersistentWorkflowTest extends TestCase {
	
	private static final Logger logger = Logger.getLogger(PersistentWorkflowTest.class);
	
	private void cleanDB(DataSource ds) throws Exception {
		new RetryingTransaction(ds) {
			@Override
			protected void execute() throws Exception {
				getConnection().createStatement().execute("DELETE FROM COP_AUDIT_TRAIL_EVENT");
				getConnection().createStatement().execute("DELETE FROM COP_WAIT");
				getConnection().createStatement().execute("DELETE FROM COP_RESPONSE");
				getConnection().createStatement().execute("DELETE FROM COP_QUEUE");
				getConnection().createStatement().execute("DELETE FROM COP_WORKFLOW_INSTANCE");
				getConnection().createStatement().execute("DELETE FROM COP_WORKFLOW_INSTANCE_ERROR");
			}
		}.run();
	}
	
	private static final String createTestData(int length) {
		StringBuilder dataSB = new StringBuilder(length);
		for (int i=0; i<length; i++) {
			int pos = (int)(Math.random()*70.0);
			dataSB.append("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01234567890!§$%&/()=?".substring(pos,pos+1));
		}
		return dataSB.toString(); 
	}

	public void testAsnychResponse() throws Exception {
		logger.info("running testAsnychResponse");
		final int NUMB = 20;
		final String DATA = createTestData(50);
		final ConfigurableApplicationContext context = new ClassPathXmlApplicationContext(new String[] {"persistent-engine-unittest-context.xml", "unittest-context.xml"});
		cleanDB(context.getBean(DataSource.class));
		final PersistentScottyEngine engine = context.getBean(PersistentScottyEngine.class);
		engine.startup();
		final BackChannelQueue backChannelQueue = context.getBean(BackChannelQueue.class);
		try {
			assertEquals(EngineState.STARTED,engine.getEngineState());
			
			for (int i=0; i<NUMB; i++) {
				WorkflowFactory<String> wfFactory = engine.createWorkflowFactory(PersistentUnitTestWorkflow.class.getName());
				Workflow<String> wf = wfFactory.newInstance();
				wf.setData(DATA);
				engine.run(wf);
			}

			for (int i=0; i<NUMB; i++) {
				WorkflowResult x = backChannelQueue.dequeue(60, TimeUnit.SECONDS);
				Assert.assertNotNull(x);
				Assert.assertNotNull(x.getResult());
				Assert.assertNotNull(x.getResult().toString().length() == DATA.length());
				Assert.assertNull(x.getException());
			}
		}
		finally {
			context.close();
		}
		assertEquals(EngineState.STOPPED,engine.getEngineState());
		
	}

	public void testAsnychResponseLargeData() throws Exception {
		logger.info("running testAsnychResponse");
		final int NUMB = 20;
		final String DATA = createTestData(65536);
		final ConfigurableApplicationContext context = new ClassPathXmlApplicationContext(new String[] {"persistent-engine-unittest-context.xml", "unittest-context.xml"});
		cleanDB(context.getBean(DataSource.class));
		final PersistentScottyEngine engine = context.getBean(PersistentScottyEngine.class);
		engine.startup();
		final BackChannelQueue backChannelQueue = context.getBean(BackChannelQueue.class);
		try {
			assertEquals(EngineState.STARTED,engine.getEngineState());
			
			for (int i=0; i<NUMB; i++) {
				WorkflowFactory<String> wfFactory = engine.createWorkflowFactory(PersistentUnitTestWorkflow.class.getName());
				Workflow<String> wf = wfFactory.newInstance();
				wf.setData(DATA);
				engine.run(wf);
			}

			for (int i=0; i<NUMB; i++) {
				WorkflowResult x = backChannelQueue.dequeue(60, TimeUnit.SECONDS);
				Assert.assertNotNull(x);
				Assert.assertNotNull(x.getResult());
				Assert.assertNotNull(x.getResult().toString().length() == DATA.length());
				Assert.assertNull(x.getException());
			}
		}
		finally {
			context.close();
		}
		assertEquals(EngineState.STOPPED,engine.getEngineState());
		
	}

	public void testWithConnection() throws Exception {
		logger.info("running testWithConnection");
		final int NUMB = 20;
		final ConfigurableApplicationContext context = new ClassPathXmlApplicationContext(new String[] {"persistent-engine-unittest-context.xml", "unittest-context.xml"});
		final DataSource ds = context.getBean(DataSource.class);
		cleanDB(ds);
		final PersistentScottyEngine engine = context.getBean(PersistentScottyEngine.class);
		engine.startup();
		final BackChannelQueue backChannelQueue = context.getBean(BackChannelQueue.class);
		try {
			assertEquals(EngineState.STARTED,engine.getEngineState());

			new RetryingTransaction(ds) {
				@Override
				protected void execute() throws Exception {
					for (int i=0; i<NUMB; i++) {
						WorkflowFactory<?> wfFactory = engine.createWorkflowFactory(PersistentUnitTestWorkflow.class.getName());
						Workflow<?> wf = wfFactory.newInstance();
						engine.run(wf,getConnection());
					}
				}
			}.run();

			for (int i=0; i<NUMB; i++) {
				WorkflowResult x = backChannelQueue.dequeue(60, TimeUnit.SECONDS);
				Assert.assertNotNull(x);
				Assert.assertNull(x.getResult());
				Assert.assertNull(x.getException());
			}
		}
		finally {
			context.close();
		}
		assertEquals(EngineState.STOPPED,engine.getEngineState());
		
	}
	
	public void testWithConnectionBulkInsert() throws Exception {
		logger.info("running testWithConnectionBulkInsert");
		final int NUMB = 50;
		final ConfigurableApplicationContext context = new ClassPathXmlApplicationContext(new String[] {"persistent-engine-unittest-context.xml", "unittest-context.xml"});
		final DataSource ds = context.getBean(DataSource.class);
		cleanDB(ds);
		final PersistentScottyEngine engine = context.getBean(PersistentScottyEngine.class);
		engine.startup();
		final BackChannelQueue backChannelQueue = context.getBean(BackChannelQueue.class);
		try {
			assertEquals(EngineState.STARTED,engine.getEngineState());

			final List<Workflow<?>> list = new ArrayList<Workflow<?>>();
			for (int i=0; i<NUMB; i++) {
				WorkflowFactory<?> wfFactory = engine.createWorkflowFactory(PersistentUnitTestWorkflow.class.getName());
				Workflow<?> wf = wfFactory.newInstance();
				list.add(wf);
			}
			
			new RetryingTransaction(ds) {
				@Override
				protected void execute() throws Exception {
					engine.run(list,getConnection());
				}
			}.run();

			for (int i=0; i<NUMB; i++) {
				WorkflowResult x = backChannelQueue.dequeue(60, TimeUnit.SECONDS);
				Assert.assertNotNull(x);
				Assert.assertNull(x.getResult());
				Assert.assertNull(x.getException());
			}
		}
		finally {
			context.close();
		}
		assertEquals(EngineState.STOPPED,engine.getEngineState());
		
	}
	
	public void testTimeouts() throws Exception {
		logger.info("running testTimeouts");
		final int NUMB = 10;
		final ConfigurableApplicationContext context = new ClassPathXmlApplicationContext(new String[] {"persistent-engine-unittest-context.xml", "unittest-context.xml"});
		cleanDB(context.getBean(DataSource.class));
		final PersistentScottyEngine engine = context.getBean(PersistentScottyEngine.class);
		engine.startup();
		final BackChannelQueue backChannelQueue = context.getBean(BackChannelQueue.class);
		try {
			assertEquals(EngineState.STARTED,engine.getEngineState());
			
			for (int i=0; i<NUMB; i++) {
				WorkflowFactory<?> wfFactory = engine.createWorkflowFactory(TimingOutPersistentUnitTestWorkflow.class.getName());
				Workflow<?> wf = wfFactory.newInstance();
				engine.run(wf);
			}

			for (int i=0; i<NUMB; i++) {
				WorkflowResult x = backChannelQueue.dequeue(60, TimeUnit.SECONDS);
				Assert.assertNotNull(x);
				Assert.assertNull(x.getResult());
				Assert.assertNull(x.getException());
			}
		}
		finally {
			context.close();
		}
		assertEquals(EngineState.STOPPED,engine.getEngineState());
		
	}
	
	public void testMultipleEngines() throws Exception {
		logger.info("running testMultipleEngines");
		final int NUMB = 50;
		final ConfigurableApplicationContext contextA = new ClassPathXmlApplicationContext(new String[] {"persistent-engine-unittest-context.xml", "unittest-context.xml"});
		final ConfigurableApplicationContext contextB = new ClassPathXmlApplicationContext(new String[] {"persistent-engine-unittest-context.xml", "unittest-context.xml"});
		cleanDB(contextA.getBean(DataSource.class));
		final PersistentScottyEngine engineA = contextA.getBean(PersistentScottyEngine.class);
		final PersistentScottyEngine engineB = contextB.getBean(PersistentScottyEngine.class);
		final BackChannelQueue backChannelQueueA = contextA.getBean(BackChannelQueue.class);
		final BackChannelQueue backChannelQueueB = contextB.getBean(BackChannelQueue.class);
		engineA.startup();
		engineB.startup();
		try {
			assertEquals(EngineState.STARTED,engineA.getEngineState());
			assertEquals(EngineState.STARTED,engineB.getEngineState());
			
			for (int i=0; i<NUMB; i++) {
				ProcessingEngine engine = i % 2 == 0 ? engineA : engineB;
				WorkflowFactory<?> wfFactory = engine.createWorkflowFactory(PersistentUnitTestWorkflow.class.getName());
				Workflow<?> wf = wfFactory.newInstance();
				engine.run(wf);
			}

			int x=0;
			long startTS = System.currentTimeMillis();
			while (x < NUMB && startTS+60000 > System.currentTimeMillis()) {
				WorkflowResult wfr = backChannelQueueA.poll();
				if (wfr == null) {
					wfr = backChannelQueueB.poll();
				}
				if (wfr != null) {
					Assert.assertNull(wfr.getResult());
					Assert.assertNull(wfr.getException());
					x++;
				}
				else {
					Thread.sleep(50);
				}
			}
			if (x != NUMB) {
				fail("Test failed - Timeout - "+x+" responses so far");
			}
			Thread.sleep(1000);
			
			// check for late queue entries
			assertNull(backChannelQueueA.poll());
			assertNull(backChannelQueueB.poll());
			
			// check AuditTrail Log
			new RetryingTransaction(contextA.getBean(DataSource.class)) {
				@Override
				protected void execute() throws Exception {
					ResultSet rs = getConnection().createStatement().executeQuery("SELECT count(*) FROM COP_AUDIT_TRAIL_EVENT");
					rs.next();
					int count = rs.getInt(1);
					assertEquals(NUMB*11, count);
				}
			}.run();
		}
		finally {
			contextA.close();
			contextB.close();
		}
		assertEquals(EngineState.STOPPED,engineA.getEngineState());
		assertEquals(EngineState.STOPPED,engineB.getEngineState());
		
	}
	
	public void testErrorHandlingInCoreEngine() throws Exception {
		final ConfigurableApplicationContext context = new ClassPathXmlApplicationContext(new String[] {"persistent-engine-unittest-context.xml", "unittest-context.xml"});
		try {
			cleanDB(context.getBean(DataSource.class));
			final PersistentScottyEngine engine = context.getBean(PersistentScottyEngine.class);
			engine.startup();
			WorkflowFactory<?> wfFactory = engine.createWorkflowFactory(de.scoopgmbh.copper.test.persistent.ExceptionThrowingPersistentUnitTestWorkflow.class.getName());
			final Workflow<?> wf = wfFactory.newInstance();
			engine.run(wf);
			Thread.sleep(2000);
			//check
			new RetryingTransaction(context.getBean(DataSource.class)) {
				@Override
				protected void execute() throws Exception {
					ResultSet rs = getConnection().createStatement().executeQuery("select * from cop_workflow_instance_error");
					assertTrue(rs.next());
					assertEquals(wf.getId(), rs.getString("WORKFLOW_INSTANCE_ID"));
					assertNotNull(rs.getString("EXCEPTION"));
					assertFalse(rs.next());
				}
			}.run();
			engine.restart(wf.getId());
			Thread.sleep(2000);
			new RetryingTransaction(context.getBean(DataSource.class)) {
				@Override
				protected void execute() throws Exception {
					ResultSet rs = getConnection().createStatement().executeQuery("select * from cop_workflow_instance_error");
					assertTrue(rs.next());
					assertEquals(wf.getId(), rs.getString("WORKFLOW_INSTANCE_ID"));
					assertNotNull(rs.getString("EXCEPTION"));
					assertTrue(rs.next());
					assertEquals(wf.getId(), rs.getString("WORKFLOW_INSTANCE_ID"));
					assertNotNull(rs.getString("EXCEPTION"));
					assertFalse(rs.next());
				}
			}.run();
		}
		finally {
			context.close();
		}
	}
	
	
	public void testParentChildWorkflow() throws Exception {
		logger.info("running testParentChildWorkflow");
		final int NUMB = 20;
		final ConfigurableApplicationContext context = new ClassPathXmlApplicationContext(new String[] {"persistent-engine-unittest-context.xml", "unittest-context.xml"});
		cleanDB(context.getBean(DataSource.class));
		final PersistentScottyEngine engine = context.getBean(PersistentScottyEngine.class);
		engine.startup();
		final BackChannelQueue backChannelQueue = context.getBean(BackChannelQueue.class);
		try {
			assertEquals(EngineState.STARTED,engine.getEngineState());
			
			for (int i=0; i<NUMB; i++) {
				WorkflowFactory<?> wfFactory = engine.createWorkflowFactory(TestParentWorkflow.class.getName());
				Workflow<?> wf = wfFactory.newInstance();
				engine.run(wf);
			}

			for (int i=0; i<NUMB; i++) {
				WorkflowResult x = backChannelQueue.dequeue(60, TimeUnit.SECONDS);
				Assert.assertNotNull("Timeout!",x);
				Assert.assertNull(x.getResult());
				Assert.assertNull(x.getException());
			}
		}
		finally {
			context.close();
		}
		assertEquals(EngineState.STOPPED,engine.getEngineState());
	}	
}