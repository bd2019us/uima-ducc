/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package org.apache.uima.ducc.transport.configuration.jp;
import java.io.InvalidClassException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NoHttpResponseException;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.pool.BasicConnPool;
import org.apache.http.util.EntityUtils;
import org.apache.uima.ducc.common.IDuccUser;
import org.apache.uima.ducc.common.NodeIdentity;
import org.apache.uima.ducc.common.utils.DuccLogger;
import org.apache.uima.ducc.common.utils.XStreamUtils;
import org.apache.uima.ducc.container.net.iface.IMetaCasTransaction;
import org.apache.uima.ducc.container.net.iface.IMetaCasTransaction.Direction;
import org.apache.uima.ducc.container.net.iface.IMetaCasTransaction.Type;
import org.apache.uima.ducc.container.net.iface.IPerformanceMetrics;
import org.apache.uima.ducc.container.net.impl.MetaCasTransaction;
import org.apache.uima.ducc.container.net.impl.PerformanceMetrics;
import org.apache.uima.ducc.container.net.impl.TransactionId;
import org.apache.uima.ducc.container.sd.ConfigurationProperties;
import org.apache.uima.ducc.container.sd.ServiceRegistry;
import org.apache.uima.ducc.container.sd.ServiceRegistry_impl;
import org.apache.uima.ducc.container.sd.iface.ServiceDriver;

public class DuccHttpClient {
	private DuccLogger logger = new DuccLogger(DuccHttpClient.class);
	private JobProcessComponent duccComponent;
	private BasicConnPool connPool = null;
	private HttpHost host = null;
	private NodeIdentity nodeIdentity;
	private String pid = "";
	private ReentrantLock lock = new ReentrantLock();
	private HttpClient httpClient = null;
	private String jdUrl;
	private PoolingHttpClientConnectionManager cMgr = null;

	private int ClientMaxConnections = 0;
	private int ClientMaxConnectionsPerRoute = 0;
	private int ClientMaxConnectionsPerHostPort = 0;
  private ServiceRegistry registry = null;
  private String taskServerName;
  private Properties config = null;
	
  public DuccHttpClient(JobProcessComponent duccComponent) {
    this.duccComponent = duccComponent;
  }
  
	public void setScaleout(int scaleout) {
		connPool.setMaxTotal(scaleout);
		connPool.setDefaultMaxPerRoute(scaleout);
		connPool.setMaxPerRoute(host, scaleout);
	}
	
	// If no registry use the url in the system properties, e.g. JD/JP case
	// The fetch should return one of the values saved by the most recent notification.
	// but should block if no instance currently registered.
	public String getJdUrl() {
	  if (registry == null) {
	    return jdUrl;
	  }
	  String address = registry.fetch(taskServerName);   // Will block if none registered
	  logger.info("getJdUrl", null, "Registry entry for", taskServerName, "is", address);
	  return address;
	}
	
	public void initialize(String jdUrl) throws Exception {

	  // If not specified get the url from the registry
	  if (jdUrl == null || jdUrl.isEmpty()) {
	    config = ConfigurationProperties.getProperties();    // Holds registry details AND service.type
	    String registryLocn = config.getProperty(ServiceDriver.Registry);
	    taskServerName = config.getProperty(ServiceDriver.Application);
	    if (registryLocn != null && taskServerName != null) {
	      registry = ServiceRegistry_impl.getInstance();
	      if (!registry.initialize(registryLocn)) {
	        registry = null;
	      }
	    } 
	    if (registry == null) {
	      throw new RuntimeException("Failed to connect to registry at "+registryLocn+" to locate server "+taskServerName);
	    }
	    logger.info("initialize", null, "Using registry at", registryLocn, "to locate server", taskServerName);
	    jdUrl = getJdUrl();
	  }
	  this.jdUrl = jdUrl;
		
		logger.info("initialize", null, "Found jdUrl =", jdUrl);
		
		int pos = jdUrl.indexOf("//");
        int ipEndPos = jdUrl.indexOf(":", pos);
        String jdIP = jdUrl.substring(pos+2,ipEndPos);
        int portEndPos = jdUrl.indexOf("/", ipEndPos);
        String jdScheme = jdUrl.substring(portEndPos+1);
        String jdPort = jdUrl.substring(ipEndPos+1, portEndPos);

		
		pid = getProcessIP("N/A");
		nodeIdentity = new NodeIdentity();
		cMgr = new PoolingHttpClientConnectionManager();
		
        if(ClientMaxConnections > 0) {
            cMgr.setMaxTotal(ClientMaxConnections);
        }
        // Set default max connections per route                                                                                                                   
        if(ClientMaxConnectionsPerRoute > 0) {
            cMgr.setDefaultMaxPerRoute(ClientMaxConnectionsPerRoute);
        }
		
		// Set max connections for host:port                                                                                                                       
        
        HttpHost httpHost = new HttpHost(jdIP, Integer.valueOf(jdPort),jdScheme);
        if(ClientMaxConnectionsPerHostPort > 0) {
          cMgr.setMaxPerRoute(new HttpRoute(httpHost), ClientMaxConnectionsPerHostPort);
        }

        httpClient = HttpClients.custom().setConnectionManager(cMgr).build();

	}
	public void stop() throws Exception {
		if ( cMgr != null ) {
			cMgr.shutdown();
		}
	}
//	public void testConnection() throws Exception {
//		// test connection to the JD
//	    Future<BasicPoolEntry> future = connPool.lease(host,  null);
//		BasicPoolEntry poolEntry = null;
//		try {
//			poolEntry= future.get();
//		} finally {
//			connPool.release(poolEntry, true);
//		}
//	}
	public void close() {
    	try {
        //	conn.close();
    		
    	} catch( Exception e) {
    		e.printStackTrace();
    	}
    }
	private String getProcessIP(final String fallback) {
		// the following code returns '<pid>@<hostname>'
		String name = ManagementFactory.getRuntimeMXBean().getName();
		int pos = name.indexOf('@');

		if (pos < 1) {
			// pid not found
			return fallback;
		}

		try {
			return Long.toString(Long.parseLong(name.substring(0, pos)));
		} catch (NumberFormatException e) {
			// ignore
		}
		return fallback;
	}
	private String getIP() {
		String ip =nodeIdentity.getIp();
		if ( System.getenv(IDuccUser.EnvironmentVariable.DUCC_IP.value()) != null) {
			ip = System.getenv(IDuccUser.EnvironmentVariable.DUCC_IP.value());
		}
		return ip;
	}
	private String getNodeName() {
		String nn =nodeIdentity.getName();
		if ( System.getenv(IDuccUser.EnvironmentVariable.DUCC_NODENAME.value()) != null) {
			nn = System.getenv(IDuccUser.EnvironmentVariable.DUCC_NODENAME.value());
		}
		return nn;
	}
	private String getProcessName() {
	  String pn = System.getenv(IDuccUser.EnvironmentVariable.DUCC_ID_PROCESS.value());
	  if (config != null && config.containsKey("service.type")) {
	    pn = config.getProperty("service.type");  // Indicates the type of service request
	  }
		return pn;
	}
	
    private void addCommonHeaders( IMetaCasTransaction transaction ) {
    	String location = "addCommonHeaders";
    	transaction.setRequesterAddress(getIP());
    	transaction.setRequesterNodeName(getNodeName());
    	transaction.setRequesterProcessName(getProcessName());
    	transaction.setRequesterProcessId(Integer.valueOf(pid));
    	transaction.setRequesterThreadId((int)Thread.currentThread().getId());
    	logger.trace(location, null, "ip:"+transaction.getRequesterAddress());
    	logger.trace(location, null, "nodeName:"+transaction.getRequesterNodeName());
    	logger.trace(location, null, "processName:"+transaction.getRequesterProcessName());
    	logger.trace(location, null, "processId:"+transaction.getRequesterProcessId());
    	logger.trace(location, null, "threadId:"+transaction.getRequesterThreadId());
    }
    
    private void addCommonHeaders( HttpPost method ) {
    	synchronized( DuccHttpClient.class) {
    		
    		 method.setHeader("IP", getIP());
             method.setHeader("Hostname", getNodeName());
             method.setHeader("ThreadID",
                             String.valueOf(Thread.currentThread().getId()));
             method.setHeader("PID", pid);
    		
    	}
		
    }

	public IMetaCasTransaction execute( IMetaCasTransaction transaction, HttpPost postMethod ) throws Exception {
		Exception lastError = null;
		IMetaCasTransaction reply=null;

		addCommonHeaders(transaction);
		transaction.setDirection(Direction.Request);
		
		try {
				// Serialize request object to XML
				String body = XStreamUtils.marshall(transaction);
	            HttpEntity e = new StringEntity(body,ContentType.APPLICATION_XML); //, "application/xml","UTF-8" );
	         
	            postMethod.setEntity(e);
	            
	            addCommonHeaders(postMethod);
	    
	            logger.debug("execute",null, "calling httpClient.executeMethod()");
	            // wait for a reply. When connection fails, retry indefinitely
	            HttpResponse response = null;
	            try {
	            	 response = httpClient.execute(postMethod);
	            } catch( HttpHostConnectException ex) {
	            	// Lost connection to the Task Allocation App
	            	// Block until connection is restored
	            	response = retryUntilSuccessfull(transaction, postMethod);
	            } catch( NoHttpResponseException ex ) {
	            	// Lost connection to the Task Allocation App
	            	// Block until connection is restored
	            	response = retryUntilSuccessfull(transaction, postMethod);
	            	
	            }
	            // we may have blocked in retryUntilSuccessfull while this process
	            // was told to stop
	            if ( !duccComponent.isRunning() ) {
	            	return null;
	            }
	            logger.debug("execute",null, "httpClient.executeMethod() returned");
	            HttpEntity entity = response.getEntity();
                String content = EntityUtils.toString(entity);
                StatusLine statusLine = response.getStatusLine();
                if ( statusLine.getStatusCode() != 200) {
                    logger.error("execute", null, "Unable to Communicate with JD - Error:"+statusLine);
                    logger.error("execute", null, "Content causing error:"+content);
                    System.out.println("Thread::"+Thread.currentThread().getId()+" ERRR::Content causing error:"+content);
                    throw new RuntimeException("JP Http Client Unable to Communicate with JD - Error:"+statusLine);
                }
                logger.debug("execute", null, "Thread:"+Thread.currentThread().getId()+" JD Reply Status:"+statusLine);
                logger.debug("execute", null, "Thread:"+Thread.currentThread().getId()+" Recv'd:"+content);

				Object o;
				try {
					o = XStreamUtils.unmarshall(content); //sb.toString());
					
				} catch( Exception ex) {
					logger.error("execute", null, "Thread:"+Thread.currentThread().getId()+" ERRR::Content causing error:"+content,ex);
					throw ex;
				}
				if ( o instanceof IMetaCasTransaction) {
					reply = (MetaCasTransaction)o;
				} else {
					throw new InvalidClassException("Expected IMetaCasTransaction - Instead Received "+o.getClass().getName());
				}
		} catch( Exception t) {
			lastError = t;
		}
		finally {
			postMethod.releaseConnection();
		}
		if ( reply != null ) {
			return reply;
		} else {
			if ( lastError != null ){
				throw lastError;

			} else {
				throw new RuntimeException("Shouldn't happen ");
			}
		} 
	}

	private HttpResponse retryUntilSuccessfull(IMetaCasTransaction transaction, HttpPost postMethod) throws Exception {
        HttpResponse response=null;
		// Only one thread attempts recovery. Other threads will block here
		// until connection to the remote is restored. 
		logger.error("retryUntilSucessfull", null, "Connection Lost to", postMethod.getURI(), "- Retrying Until Successful ...");
   		lock.lock();

         // retry indefinitely
		while( duccComponent.isRunning() ) {
       		try {
       			// retry the command
       		  jdUrl = getJdUrl();
       		  URI jdUri = new URI(jdUrl);
       		  postMethod.setURI(jdUri);
       		  logger.warn("retryUntilSucessfull", null, "Trying to connect to", jdUrl);
       			response = httpClient.execute(postMethod);
       			logger.warn("retryUntilSucessfull", null, "Recovered Connection");

       			// success, so release the lock so that other waiting threads
       			// can retry command
       		    if ( lock.isHeldByCurrentThread()) {
        		    lock.unlock();
    		    }

       			break;
       			
       		} catch( HttpHostConnectException exx ) {
       			// Connection still not available so sleep awhile
       			synchronized(postMethod) {
       			  logger.warn("retryUntilSucessfull", null, "Connection failed - retry in", duccComponent.getThreadSleepTime()/1000, "secs");
       				postMethod.wait(duccComponent.getThreadSleepTime());
       			}
       		}
        }
		return response;
	}

	public static void main(String[] args) {
		try {
			HttpPost postMethod = new HttpPost(args[0]);
			DuccHttpClient client = new DuccHttpClient(null);
		//	client.setScaleout(10);
			//client.setTimeout(30000);
			client.initialize(args[0]);
			int minor = 0;
			IMetaCasTransaction transaction = new MetaCasTransaction();
			AtomicInteger seq = new AtomicInteger(0);
			TransactionId tid = new TransactionId(seq.incrementAndGet(), minor);
			transaction.setTransactionId(tid);
			// According to HTTP spec, GET may not contain Body in 
			// HTTP request. HttpClient actually enforces this. So
			// do a POST instead of a GET.
			transaction.setType(Type.Get);  // Tell JD you want a Work Item
			//String command = Type.Get.name();
	    	System.out.println("HttpWorkerThread.run() "+ "Thread Id:"+Thread.currentThread().getId()+" Requesting next WI from JD");;
			// send a request to JD and wait for a reply
	    	transaction = client.execute(transaction, postMethod);
	        // The JD may not provide a Work Item to process.
	    	if ( transaction.getMetaCas()!= null) {
	    		System.out.println("run Thread:"+Thread.currentThread().getId()+" Recv'd WI:"+transaction.getMetaCas().getSystemKey());
				System.out.println("CAS:"+transaction.getMetaCas().getUserSpaceCas());
	    		// Confirm receipt of the CAS. 
				transaction.setType(Type.Ack);
				//command = Type.Ack.name();
				tid = new TransactionId(seq.incrementAndGet(), minor++);
				transaction.setTransactionId(tid);
				System.out.println("run  Thread:"+Thread.currentThread().getId()+" Sending ACK request - WI:"+transaction.getMetaCas().getSystemKey());
				transaction = client.execute(transaction, postMethod); 
				if ( transaction.getMetaCas() == null) {
					// this can be the case when a JD receives ACK late 
					System.out.println("run Thread:"+Thread.currentThread().getId()+" ACK reply recv'd, however there is no MetaCas. The JD Cancelled the transaction");
				} else {
					System.out.println("run Thread:"+Thread.currentThread().getId()+" ACK reply recv'd");
				}

	        }
			transaction.setType(Type.End);
			//command = Type.End.name();
			tid = new TransactionId(seq.incrementAndGet(), minor++);
			transaction.setTransactionId(tid);
			IPerformanceMetrics metricsWrapper =
					new PerformanceMetrics();
			
			metricsWrapper.set(Arrays.asList(new Properties()));
			transaction.getMetaCas().setPerformanceMetrics(metricsWrapper);
			
			System.out.println("run  Thread:"+Thread.currentThread().getId()+" Sending END request - WI:"+transaction.getMetaCas().getSystemKey());
			transaction = client.execute(transaction, postMethod); 

			
		} catch( Exception e) {
			e.printStackTrace();
		}
	}
	
}
