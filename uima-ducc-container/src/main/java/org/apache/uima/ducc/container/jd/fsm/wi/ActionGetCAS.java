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
package org.apache.uima.ducc.container.jd.fsm.wi;

import org.apache.uima.ducc.container.common.DuccLogger;
import org.apache.uima.ducc.container.common.IDuccId;
import org.apache.uima.ducc.container.common.IDuccLogger;
import org.apache.uima.ducc.container.common.MessageBuffer;
import org.apache.uima.ducc.container.common.Standardize;
import org.apache.uima.ducc.container.common.fsm.iface.IAction;
import org.apache.uima.ducc.container.jd.JobDriverCasManager;
import org.apache.uima.ducc.container.jd.JobDriverCommon;
import org.apache.uima.ducc.container.jd.dispatch.IRemoteWorkerIdentity;
import org.apache.uima.ducc.container.jd.dispatch.IWorkItem;
import org.apache.uima.ducc.container.jd.dispatch.RemoteWorkerIdentity;
import org.apache.uima.ducc.container.net.iface.IMetaCas;
import org.apache.uima.ducc.container.net.iface.IMetaCasTransaction;

public class ActionGetCAS implements IAction {
	
	private IDuccLogger logger = DuccLogger.getLogger(ActionGetCAS.class, IDuccLogger.Component.JD.name());
	
	@Override
	public String getName() {
		return ActionGetCAS.class.getName();
	}

	@Override
	public void engage(Object objectData) {
		String location = "engage";
		logger.debug(location, IDuccId.null_id, "");
		IActionData actionData = (IActionData) objectData;
		try {
			IWorkItem wi = actionData.getWorkItem();
			IMetaCasTransaction trans = actionData.getMetaCasTransaction();
			IRemoteWorkerIdentity rwi = new RemoteWorkerIdentity(trans);
			//
			JobDriverCasManager jdcm = JobDriverCommon.getInstance().getCasManager();
			IMetaCas metaCas = jdcm.getMetaCas();
			trans.setMetaCas(metaCas);
			//
			if(metaCas != null) {
				wi.setTodGet();
				MessageBuffer mb = new MessageBuffer();
				mb.append(Standardize.Label.transNo.get()+trans.getTransactionId().toString());
				mb.append(Standardize.Label.seqNo.get()+metaCas.getSystemKey());
				mb.append(Standardize.Label.remote.get()+rwi.toString());
				logger.info(location, IDuccId.null_id, mb.toString());
			}
			else {MessageBuffer mb = new MessageBuffer();
				mb.append("No CAS found for processing");
				logger.info(location, IDuccId.null_id, mb.toString());
			}
			
		}
		catch(Exception e) {
			logger.error(location, IDuccId.null_id, e);
		}
		
	}

}
